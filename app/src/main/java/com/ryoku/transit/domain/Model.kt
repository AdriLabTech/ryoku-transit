package com.ryoku.transit.domain

import java.time.LocalDate

data class Route(
    val id: String,
    val shortName: String,
    val longName: String,
    val color: Long,
    val textColor: Long,
)

data class Stop(
    val id: String,
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
)

/** Cómo se ha obtenido la hora de un autobús. Hoy solo existe horario publicado. */
enum class Basis {
    /** Horario del GTFS vigente para la fecha de hoy. */
    SCHEDULED,

    /** Horario de un día de referencia del feed porque el feed no cubre hoy. No es información de hoy. */
    REFERENCE,
}

data class Arrival(
    val routeId: String,
    val routeShortName: String,
    val routeColor: Long,
    val routeTextColor: Long,
    val headsign: String,
    val direction: Int,
    val tripId: String,
    /** Segundos desde el inicio del día de servicio (puede superar 86400). */
    val scheduledSec: Int,
    val minutes: Int,
    val serviceDate: LocalDate,
    val basis: Basis,
)

enum class FeedStatus { VALID, EXPIRED, NOT_YET_VALID }

data class FeedInfo(
    val source: String,
    val sha256: String,
    val start: LocalDate,
    val end: LocalDate,
    val builtAt: String,
) {
    fun status(today: LocalDate): FeedStatus = when {
        today.isBefore(start) -> FeedStatus.NOT_YET_VALID
        today.isAfter(end) -> FeedStatus.EXPIRED
        else -> FeedStatus.VALID
    }
}

/** Una parada de un viaje programado, ya filtrada por parada. */
data class ScheduledStopTime(
    val tripId: String,
    val serviceId: String,
    val routeId: String,
    val headsign: String,
    val direction: Int,
    val depSec: Int,
)

/** Lo que una fuente de datos sabe hacer. Lo que no está aquí, la app no lo ofrece. */
enum class Capability { STATIC_NETWORK, SCHEDULED_ARRIVALS, SERVICE_NOTICES }
