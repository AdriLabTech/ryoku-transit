package com.ryoku.transit.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ryoku.transit.domain.*
import java.time.LocalDate

/** Observaciones propias en el dispositivo (`observations.db`). Nunca salen del móvil; no contienen ubicación. */
class ObservationStore(context: Context) : SQLiteOpenHelper(context, "observations.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE observations(
                 id INTEGER PRIMARY KEY AUTOINCREMENT, route_id TEXT NOT NULL, direction INTEGER NOT NULL,
                 stop_id TEXT NOT NULL, trip_id TEXT NOT NULL, service_date TEXT NOT NULL,
                 scheduled_sec INTEGER NOT NULL, actual_sec INTEGER NOT NULL, basis TEXT NOT NULL,
                 source TEXT NOT NULL, recorded_at INTEGER NOT NULL,
                 UNIQUE(trip_id, stop_id, service_date, basis))""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    /** Si el viaje ya estaba anotado, se reemplaza (corrección del usuario). */
    fun save(o: Observation) {
        val v = ContentValues().apply {
            put("route_id", o.routeId); put("direction", o.direction); put("stop_id", o.stopId); put("trip_id", o.tripId)
            put("service_date", o.serviceDate.toString()); put("scheduled_sec", o.scheduledSec); put("actual_sec", o.actualSec)
            put("basis", o.basis.name); put("source", o.source.name); put("recorded_at", o.recordedAt)
        }
        writableDatabase.insertWithOnConflict("observations", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun all(): List<Observation> = buildList {
        readableDatabase.rawQuery(
            "SELECT route_id, direction, stop_id, trip_id, service_date, scheduled_sec, actual_sec, basis, source, recorded_at FROM observations ORDER BY recorded_at", null,
        ).use {
            while (it.moveToNext()) add(
                Observation(
                    it.getString(0), it.getInt(1), it.getString(2), it.getString(3), LocalDate.parse(it.getString(4)),
                    it.getInt(5), it.getInt(6), Basis.valueOf(it.getString(7)), ObservationSource.valueOf(it.getString(8)), it.getLong(9),
                ),
            )
        }
    }

    fun clear() { writableDatabase.delete("observations", null, null) }
}
