package com.ryoku.transit.data

import android.database.sqlite.SQLiteDatabase
import com.ryoku.transit.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ISO_BASIC = DateTimeFormatter.BASIC_ISO_DATE

class LatLon(val lat: Double, val lon: Double)

/** Una línea de un sentido: paradas en orden y geometría. */
class RouteDirection(val direction: Int, val headsign: String, val stops: List<Stop>, val shape: List<LatLon>)

/** Fuente de red estática y horarios programados (GTFS importado en SQLite). */
class SqliteSchedule(private val db: SQLiteDatabase) : ScheduleSource {
    val capabilities = setOf(Capability.STATIC_NETWORK, Capability.SCHEDULED_ARRIVALS)

    private val routeCache: Map<String, Route> by lazy { loadRoutes().associateBy { it.id } }

    val feed: FeedInfo by lazy {
        val m = HashMap<String, String>()
        db.rawQuery("SELECT k, v FROM meta", null).use { while (it.moveToNext()) m[it.getString(0)] = it.getString(1) }
        FeedInfo(
            m.getValue("source"), m.getValue("sha256"),
            LocalDate.parse(m.getValue("feed_start"), ISO_BASIC), LocalDate.parse(m.getValue("feed_end"), ISO_BASIC),
            m.getValue("built_at"),
        )
    }

    val calendar: ServiceCalendar by lazy {
        val weekly = HashMap<String, WeeklyService>()
        db.rawQuery("SELECT service_id, dow, start_date, end_date FROM calendar", null).use {
            while (it.moveToNext()) weekly[it.getString(0)] = WeeklyService(
                ServiceCalendar.parseDow(it.getString(1)),
                LocalDate.parse(it.getString(2), ISO_BASIC), LocalDate.parse(it.getString(3), ISO_BASIC),
            )
        }
        val ex = HashMap<String, MutableMap<LocalDate, Int>>()
        db.rawQuery("SELECT service_id, date, exception_type FROM calendar_dates", null).use {
            while (it.moveToNext()) ex.getOrPut(it.getString(0)) { HashMap() }[LocalDate.parse(it.getString(1), ISO_BASIC)] = it.getInt(2)
        }
        ServiceCalendar(weekly, ex)
    }

    private fun loadRoutes(): List<Route> = buildList {
        db.rawQuery("SELECT route_id, short_name, long_name, color, text_color FROM routes ORDER BY short_name", null).use {
            while (it.moveToNext()) add(Route(it.getString(0), it.getString(1), it.getString(2), it.getString(3).toLong(16), it.getString(4).toLong(16)))
        }
    }

    fun routes(): List<Route> = routeCache.values.sortedBy { it.shortName }
    override fun route(routeId: String) = routeCache[routeId]

    private fun stopFrom(c: android.database.Cursor) = Stop(c.getString(0), c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4))

    fun stop(id: String): Stop? =
        db.rawQuery("SELECT stop_id, code, name, lat, lon FROM stops WHERE stop_id=?", arrayOf(id)).use { if (it.moveToFirst()) stopFrom(it) else null }

    fun allStops(): List<Stop> = buildList {
        db.rawQuery("SELECT stop_id, code, name, lat, lon FROM stops ORDER BY name", null).use { while (it.moveToNext()) add(stopFrom(it)) }
    }

    /** Búsqueda por nombre (sin distinguir mayúsculas ni acentos básicos) o por código exacto. */
    fun searchStops(query: String, limit: Int = 50): List<Stop> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val all = allStopsCached
        val n = fold(q)
        return all.filter { it.code == q || fold(it.name).contains(n) }.take(limit)
    }

    private val allStopsCached by lazy { allStops() }

    private fun fold(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Líneas que paran en la parada y sus sentidos. */
    fun routesAtStop(stopId: String): List<Route> = buildList {
        db.rawQuery(
            "SELECT DISTINCT t.route_id FROM stop_times s JOIN trips t ON t.trip_idx = s.trip_idx WHERE s.stop_id=?", arrayOf(stopId),
        ).use { while (it.moveToNext()) routeCache[it.getString(0)]?.let(::add) }
    }.sortedBy { it.shortName }

    override fun stopTimes(stopId: String, fromSec: Int, toSec: Int): List<ScheduledStopTime> = buildList {
        db.rawQuery(
            """SELECT t.trip_id, t.service_id, t.route_id, t.headsign, t.direction, s.dep
               FROM stop_times s JOIN trips t ON t.trip_idx = s.trip_idx
               WHERE s.stop_id = ? AND s.arr BETWEEN ? AND ?""",
            arrayOf(stopId, fromSec.toString(), toSec.toString()),
        ).use { while (it.moveToNext()) add(ScheduledStopTime(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getInt(4), it.getInt(5))) }
    }

    /** Sentidos de una línea. Se usa el viaje con más paradas de cada sentido como representativo. */
    fun directions(routeId: String): List<RouteDirection> {
        val reps = ArrayList<Triple<Int, Int, String>>() // direction, trip_idx, headsign
        db.rawQuery(
            """SELECT t.direction, t.trip_idx, t.headsign, COUNT(*) c FROM trips t JOIN stop_times s ON s.trip_idx=t.trip_idx
               WHERE t.route_id=? GROUP BY t.trip_idx ORDER BY t.direction, c DESC""", arrayOf(routeId),
        ).use {
            val seen = HashSet<Int>()
            while (it.moveToNext()) if (seen.add(it.getInt(0))) reps += Triple(it.getInt(0), it.getInt(1), it.getString(2))
        }
        return reps.map { (dir, idx, head) ->
            val stops = buildList {
                db.rawQuery(
                    """SELECT p.stop_id, p.code, p.name, p.lat, p.lon FROM stop_times s JOIN stops p ON p.stop_id=s.stop_id
                       WHERE s.trip_idx=? ORDER BY s.seq""", arrayOf(idx.toString()),
                ).use { while (it.moveToNext()) add(stopFrom(it)) }
            }
            RouteDirection(dir, head, stops, shapeOfTrip(idx))
        }
    }

    private fun shapeOfTrip(tripIdx: Int): List<LatLon> =
        db.rawQuery("SELECT h.pts FROM trips t JOIN shapes h ON h.shape_id=t.shape_id WHERE t.trip_idx=?", arrayOf(tripIdx.toString())).use {
            if (!it.moveToFirst()) return emptyList()
            decodeShape(it.getBlob(0))
        }

    /** Todas las geometrías de la red para el mapa (una por shape). */
    fun allShapes(): List<List<LatLon>> = buildList {
        db.rawQuery("SELECT pts FROM shapes", null).use { while (it.moveToNext()) add(decodeShape(it.getBlob(0))) }
    }

    companion object {
        fun decodeShape(b: ByteArray): List<LatLon> {
            val bb = java.nio.ByteBuffer.wrap(b)
            return List(b.size / 8) { LatLon(bb.getInt() / 1e5, bb.getInt() / 1e5) }
        }
    }
}
