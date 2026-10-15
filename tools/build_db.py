#!/usr/bin/env python3
"""Importa un GTFS estático de AUCORSA y genera la base SQLite que empaqueta la app.

Uso: python3 tools/build_db.py <gtfs.zip> <salida.db> [--report informe.json]

Solo se importan los ficheros que existen en el feed. Los defectos de calidad se
cuentan y se vuelcan al informe; los errores estructurales abortan la importación.
"""
import argparse, csv, hashlib, io, json, sqlite3, struct, sys, zipfile
from datetime import datetime, timezone

REQUIRED = ["agency.txt", "routes.txt", "stops.txt", "trips.txt", "stop_times.txt"]


def rows(z, name):
    with z.open(name) as fh:
        yield from csv.DictReader(io.TextIOWrapper(fh, encoding="utf-8-sig", newline=""))


def hms(s):
    """HH:MM:SS (HH puede superar 24) -> segundos; None si vacío o inválido."""
    s = (s or "").strip()
    if not s:
        return None
    try:
        h, m, sec = s.split(":")
        return int(h) * 3600 + int(m) * 60 + int(sec)
    except ValueError:
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("gtfs")
    ap.add_argument("out")
    ap.add_argument("--report")
    ap.add_argument("--source", default="NAP/Mobility Database mdb-2713")
    a = ap.parse_args()

    sha = hashlib.sha256(open(a.gtfs, "rb").read()).hexdigest()
    z = zipfile.ZipFile(a.gtfs)
    names = set(z.namelist())
    missing = [n for n in REQUIRED if n not in names]
    if missing:
        sys.exit(f"feed incompleto, faltan: {missing}")

    defects = {}

    def defect(k, n=1):
        defects[k] = defects.get(k, 0) + n

    import os
    if os.path.exists(a.out):
        os.remove(a.out)
    db = sqlite3.connect(a.out)
    db.executescript("""
    PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
    CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL);
    CREATE TABLE routes(route_id TEXT PRIMARY KEY, short_name TEXT NOT NULL, long_name TEXT NOT NULL,
                        color TEXT NOT NULL, text_color TEXT NOT NULL);
    CREATE TABLE stops(stop_id TEXT PRIMARY KEY, code TEXT NOT NULL, name TEXT NOT NULL,
                       lat REAL NOT NULL, lon REAL NOT NULL);
    CREATE TABLE trips(trip_idx INTEGER PRIMARY KEY, trip_id TEXT NOT NULL, route_id TEXT NOT NULL,
                       service_id TEXT NOT NULL, headsign TEXT NOT NULL, direction INTEGER NOT NULL,
                       shape_id TEXT);
    CREATE TABLE stop_times(trip_idx INTEGER NOT NULL, stop_id TEXT NOT NULL, seq INTEGER NOT NULL,
                            arr INTEGER NOT NULL, dep INTEGER NOT NULL, PRIMARY KEY(trip_idx, seq));
    CREATE TABLE calendar(service_id TEXT PRIMARY KEY, dow TEXT NOT NULL, start_date TEXT NOT NULL,
                          end_date TEXT NOT NULL);
    CREATE TABLE calendar_dates(service_id TEXT NOT NULL, date TEXT NOT NULL, exception_type INTEGER NOT NULL,
                                PRIMARY KEY(service_id, date));
    CREATE TABLE shapes(shape_id TEXT PRIMARY KEY, pts BLOB NOT NULL);
    """)

    tz = next(rows(z, "agency.txt"))["agency_timezone"]

    # routes
    route_ids = set()
    for r in rows(z, "routes.txt"):
        route_ids.add(r["route_id"])
        db.execute("INSERT INTO routes VALUES(?,?,?,?,?)", (
            r["route_id"], r["route_short_name"], r["route_long_name"],
            (r.get("route_color") or "666666").upper(), (r.get("route_text_color") or "FFFFFF").upper()))

    # stops
    stop_ids, seen_pos = set(), {}
    for r in rows(z, "stops.txt"):
        try:
            lat, lon = float(r["stop_lat"]), float(r["stop_lon"])
        except (ValueError, TypeError):
            defect("stop_sin_coordenadas"); continue
        if not (36.5 < lat < 38.5 and -5.6 < lon < -4.0):
            defect("stop_fuera_de_cordoba")
        key = (round(lat, 6), round(lon, 6), r["stop_name"])
        if key in seen_pos:
            defect("stop_duplicada")
        seen_pos[key] = r["stop_id"]
        stop_ids.add(r["stop_id"])
        db.execute("INSERT INTO stops VALUES(?,?,?,?,?)", (r["stop_id"], r.get("stop_code") or r["stop_id"], r["stop_name"], lat, lon))

    # calendar
    services = set()
    if "calendar.txt" in names:
        for r in rows(z, "calendar.txt"):
            if r["start_date"] > r["end_date"]:
                defect("calendar_rango_invertido")
            dow = "".join(r[d] for d in ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"])
            services.add(r["service_id"])
            db.execute("INSERT INTO calendar VALUES(?,?,?,?)", (r["service_id"], dow, r["start_date"], r["end_date"]))
    if "calendar_dates.txt" in names:
        for r in rows(z, "calendar_dates.txt"):
            services.add(r["service_id"])
            db.execute("INSERT OR REPLACE INTO calendar_dates VALUES(?,?,?)", (r["service_id"], r["date"], int(r["exception_type"])))

    # trips
    trip_idx, shape_ids = {}, set()
    for i, r in enumerate(rows(z, "trips.txt")):
        if r["route_id"] not in route_ids:
            defect("trip_ruta_inexistente"); continue
        if r["service_id"] not in services:
            defect("trip_servicio_inexistente"); continue
        trip_idx[r["trip_id"]] = i
        if r.get("shape_id"):
            shape_ids.add(r["shape_id"])
        db.execute("INSERT INTO trips VALUES(?,?,?,?,?,?,?)", (
            i, r["trip_id"], r["route_id"], r["service_id"], r["trip_headsign"], int(r.get("direction_id") or 0), r.get("shape_id") or None))

    # stop_times
    batch = []
    for r in rows(z, "stop_times.txt"):
        ti = trip_idx.get(r["trip_id"])
        if ti is None:
            defect("stop_time_trip_inexistente"); continue
        if r["stop_id"] not in stop_ids:
            defect("stop_time_parada_inexistente"); continue
        arr, dep = hms(r["arrival_time"]), hms(r["departure_time"])
        if arr is None and dep is None:
            defect("stop_time_sin_hora"); continue
        arr = dep if arr is None else arr
        dep = arr if dep is None else dep
        batch.append((ti, r["stop_id"], int(r["stop_sequence"]), arr, dep))
        if len(batch) >= 50000:
            db.executemany("INSERT INTO stop_times VALUES(?,?,?,?,?)", batch); batch = []
    db.executemany("INSERT INTO stop_times VALUES(?,?,?,?,?)", batch)

    # monotonía de tiempos dentro de cada viaje
    bad = db.execute("""SELECT COUNT(*) FROM stop_times a JOIN stop_times b
        ON a.trip_idx=b.trip_idx AND b.seq=(SELECT MIN(seq) FROM stop_times c WHERE c.trip_idx=a.trip_idx AND c.seq>a.seq)
        WHERE b.arr < a.dep""").fetchone()[0]
    if bad:
        defect("stop_time_no_monotono", bad)

    # shapes -> blob int32 (lat,lon) en e5, ordenado por secuencia
    if "shapes.txt" in names:
        pts = {}
        for r in rows(z, "shapes.txt"):
            if r["shape_id"] in shape_ids:
                pts.setdefault(r["shape_id"], []).append((int(r["shape_pt_sequence"]), float(r["shape_pt_lat"]), float(r["shape_pt_lon"])))
        for sid, p in pts.items():
            p.sort()
            blob = b"".join(struct.pack(">ii", round(la * 1e5), round(lo * 1e5)) for _, la, lo in p)
            db.execute("INSERT INTO shapes VALUES(?,?)", (sid, blob))
        for sid in shape_ids - set(pts):
            defect("trip_shape_inexistente")

    start = db.execute("SELECT MIN(start_date) FROM calendar").fetchone()[0]
    end = db.execute("SELECT MAX(end_date) FROM calendar").fetchone()[0]
    d_end = db.execute("SELECT MAX(date) FROM calendar_dates").fetchone()[0]
    end = max(end or "", d_end or "")
    meta = {
        "source": a.source, "sha256": sha, "timezone": tz,
        "feed_start": start, "feed_end": end,
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "files": ",".join(sorted(names)),
    }
    db.executemany("INSERT INTO meta VALUES(?,?)", meta.items())
    db.execute("CREATE INDEX idx_st_stop ON stop_times(stop_id, arr)")
    db.execute("CREATE INDEX idx_trips_route ON trips(route_id, direction)")
    db.commit()
    counts = {t: db.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
              for t in ["routes", "stops", "trips", "stop_times", "calendar", "calendar_dates", "shapes"]}
    db.execute("VACUUM")
    db.close()
    report = {"meta": meta, "counts": counts, "defects": defects}
    print(json.dumps(report, indent=2, ensure_ascii=False))
    if a.report:
        json.dump(report, open(a.report, "w"), indent=2, ensure_ascii=False)


if __name__ == "__main__":
    main()
