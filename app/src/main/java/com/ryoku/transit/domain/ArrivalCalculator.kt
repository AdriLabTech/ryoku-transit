package com.ryoku.transit.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Acceso a horarios programados. La implementación real lee SQLite; los tests usan un fake. */
interface ScheduleSource {
    /** Pasos por [stopId] con salida en [fromSec, toSec] segundos de día de servicio (sin filtrar por calendario). */
    fun stopTimes(stopId: String, fromSec: Int, toSec: Int): List<ScheduledStopTime>
    fun route(routeId: String): Route?
}

/**
 * Calcula próximas salidas de horario publicado. No produce predicciones en vivo.
 *
 * Los tiempos GTFS superan 24 h para viajes tras medianoche, por eso se consulta también el
 * día de servicio anterior desplazado 86 400 s.
 */
class ArrivalCalculator(
    private val source: ScheduleSource,
    private val calendar: ServiceCalendar,
    private val feed: FeedInfo,
    private val zone: ZoneId,
) {
    sealed interface Result {
        data class Ok(val arrivals: List<Arrival>, val basis: Basis, val serviceDate: LocalDate) : Result
        data class NoCoverage(val status: FeedStatus) : Result
    }

    /** Día de referencia: el último día del feed con el mismo día de la semana y servicio. */
    fun referenceDate(today: LocalDate): LocalDate? {
        var d = feed.end
        repeat(14) {
            if (d.dayOfWeek == today.dayOfWeek && !d.isBefore(feed.start) && calendar.hasService(d)) return d
            d = d.minusDays(1)
        }
        return null
    }

    fun next(
        stopId: String,
        now: ZonedDateTime,
        allowReference: Boolean,
        horizon: Duration = Duration.ofHours(3),
        limit: Int = 30,
    ): Result {
        val today = now.toLocalDate()
        val status = feed.status(today)
        val (date, basis) = when {
            status == FeedStatus.VALID -> today to Basis.SCHEDULED
            allowReference -> (referenceDate(today) ?: return Result.NoCoverage(status)) to Basis.REFERENCE
            else -> return Result.NoCoverage(status)
        }
        val nowSec = secondsOfDay(now)
        val toSec = nowSec + horizon.seconds.toInt()
        val out = ArrayList<Arrival>()
        // Día de servicio anterior (viajes que pasan de medianoche) y día de servicio actual.
        for ((svcDate, offset) in listOf(date.minusDays(1) to -86400, date to 0)) {
            val activeToday = calendar.activeServices(svcDate)
            if (activeToday.isEmpty()) continue
            for (st in source.stopTimes(stopId, nowSec - offset, toSec - offset)) {
                if (st.serviceId !in activeToday) continue
                val sec = st.depSec + offset
                if (sec < nowSec) continue
                val route = source.route(st.routeId) ?: continue
                out += Arrival(
                    route.id, route.shortName, route.color, route.textColor, st.headsign, st.direction,
                    st.tripId, sec, (sec - nowSec) / 60, svcDate, basis,
                )
            }
        }
        out.sortWith(compareBy({ it.scheduledSec }, { it.routeShortName }))
        return Result.Ok(out.take(limit), basis, date)
    }

    private fun secondsOfDay(t: ZonedDateTime): Int {
        // Segundos desde el inicio del día de servicio (mediodía − 12 h, regla GTFS), robusto ante DST.
        val base = t.toLocalDate().atTime(12, 0).atZone(zone).minusHours(12)
        return Duration.between(base, t).seconds.toInt()
    }
}
