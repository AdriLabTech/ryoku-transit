package com.ryoku.transit

import com.ryoku.transit.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val MADRID = ZoneId.of("Europe/Madrid")

private class FakeSchedule(val times: List<ScheduledStopTime>) : ScheduleSource {
    val routes = mapOf("1" to Route("1", "01", "L01: A - B", 0x166C4B, 0xFFFFFF))
    override fun stopTimes(stopId: String, fromSec: Int, toSec: Int) =
        times.filter { it.depSec in fromSec..toSec }
    override fun route(routeId: String) = routes[routeId]
}

private fun st(trip: String, svc: String, sec: Int) = ScheduledStopTime(trip, svc, "1", "DESTINO", 0, sec)

// 2026-03-02 es lunes. El servicio "L" corre de lunes a viernes; "N" es nocturno.
private fun calendar() = ServiceCalendar(
    weekly = mapOf(
        "L" to WeeklyService(ServiceCalendar.parseDow("1111100"), LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 31)),
        "S" to WeeklyService(ServiceCalendar.parseDow("0000010"), LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 31)),
    ),
    exceptions = mapOf(
        "L" to mapOf(LocalDate.of(2026, 3, 4) to 2), // quitado ese miércoles
        "X" to mapOf(LocalDate.of(2026, 3, 4) to 1), // añadido solo ese miércoles
    ),
)

private val FEED = FeedInfo("test", "abc", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 31), "2026-02-26")

class ServiceCalendarTest {
    @Test fun weeklyRespectsDayAndRange() {
        val c = calendar()
        assertTrue(c.isActive("L", LocalDate.of(2026, 3, 2)))
        assertFalse(c.isActive("L", LocalDate.of(2026, 3, 7))) // sábado
        assertFalse(c.isActive("L", LocalDate.of(2026, 4, 1))) // fuera de rango
        assertFalse(c.isActive("L", LocalDate.of(2026, 2, 24)))
    }

    @Test fun exceptionsOverrideWeekly() {
        val c = calendar()
        assertFalse(c.isActive("L", LocalDate.of(2026, 3, 4)))
        assertTrue(c.isActive("X", LocalDate.of(2026, 3, 4)))
        assertEquals(setOf("X"), c.activeServices(LocalDate.of(2026, 3, 4)))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsBadDow() { ServiceCalendar.parseDow("11") }
}

class FeedInfoTest {
    @Test fun statusByDate() {
        assertEquals(FeedStatus.VALID, FEED.status(LocalDate.of(2026, 3, 31)))
        assertEquals(FeedStatus.EXPIRED, FEED.status(LocalDate.of(2026, 4, 1)))
        assertEquals(FeedStatus.NOT_YET_VALID, FEED.status(LocalDate.of(2026, 2, 24)))
    }
}

class ArrivalCalculatorTest {
    private fun calc(vararg t: ScheduledStopTime) = ArrivalCalculator(FakeSchedule(t.toList()), calendar(), FEED, MADRID)
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, MADRID)

    @Test fun scheduledWhenFeedValid() {
        val r = calc(st("a", "L", 8 * 3600 + 10 * 60), st("b", "L", 8 * 3600 + 5 * 60))
            .next("s", at(2026, 3, 2, 8, 0), allowReference = false) as ArrivalCalculator.Result.Ok
        assertEquals(Basis.SCHEDULED, r.basis)
        assertEquals(listOf("b", "a"), r.arrivals.map { it.tripId })
        assertEquals(listOf(5, 10), r.arrivals.map { it.minutes })
    }

    @Test fun ignoresInactiveServiceAndPast() {
        val r = calc(st("sat", "S", 8 * 3600 + 600), st("past", "L", 7 * 3600))
            .next("s", at(2026, 3, 2, 8, 0), allowReference = false) as ArrivalCalculator.Result.Ok
        assertTrue(r.arrivals.isEmpty())
    }

    @Test fun includesTripsPastMidnightOfPreviousServiceDay() {
        // 00:10 del martes es 24:10 del servicio del lunes.
        val r = calc(st("late", "L", 24 * 3600 + 10 * 60))
            .next("s", at(2026, 3, 3, 0, 0), allowReference = false) as ArrivalCalculator.Result.Ok
        assertEquals(1, r.arrivals.size)
        assertEquals(10, r.arrivals[0].minutes)
        assertEquals(LocalDate.of(2026, 3, 2), r.arrivals[0].serviceDate)
    }

    @Test fun expiredFeedWithoutReferenceGivesNoCoverage() {
        val r = calc(st("a", "L", 9 * 3600)).next("s", at(2026, 10, 15, 8, 0), allowReference = false)
        assertEquals(ArrivalCalculator.Result.NoCoverage(FeedStatus.EXPIRED), r)
    }

    @Test fun expiredFeedWithReferenceIsLabelledReference() {
        // 2026-10-15 es jueves; el último jueves del feed es 2026-03-26.
        val c = calc(st("a", "L", 9 * 3600))
        assertEquals(LocalDate.of(2026, 3, 26), c.referenceDate(LocalDate.of(2026, 10, 15)))
        val r = c.next("s", at(2026, 10, 15, 8, 30), allowReference = true) as ArrivalCalculator.Result.Ok
        assertEquals(Basis.REFERENCE, r.basis)
        assertEquals(LocalDate.of(2026, 3, 26), r.serviceDate)
        assertEquals(30, r.arrivals.single().minutes)
    }

    @Test fun springForwardDstDoesNotShiftMinutes() {
        // 2026-03-29: cambio a horario de verano; el día de servicio empieza a las 23:00 locales del día anterior.
        val feed = FEED.copy(end = LocalDate.of(2026, 4, 30))
        val cal = ServiceCalendar(
            mapOf("D" to WeeklyService(ServiceCalendar.parseDow("0000001"), LocalDate.of(2026, 2, 25), LocalDate.of(2026, 4, 30))),
            emptyMap(),
        )
        val c = ArrivalCalculator(FakeSchedule(listOf(st("a", "D", 8 * 3600 - 3600))), cal, feed, MADRID)
        // GTFS: 25200 s tras "mediodía − 12 h" = 07:00 CEST reales. A las 06:30 CEST faltan 30 min.
        val r = c.next("s", ZonedDateTime.of(2026, 3, 29, 6, 30, 0, 0, MADRID), allowReference = false) as ArrivalCalculator.Result.Ok
        assertEquals(30, r.arrivals.single().minutes)
    }
}
