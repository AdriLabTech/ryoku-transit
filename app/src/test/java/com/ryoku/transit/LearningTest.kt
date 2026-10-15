package com.ryoku.transit

import com.ryoku.transit.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

private val MON = LocalDate.of(2026, 3, 2) // lunes

private fun obs(err: Int, t: Long, route: String = "2", stop: String = "s1", sched: Int = 8 * 3600, basis: Basis = Basis.SCHEDULED) =
    Observation(route, 0, stop, "t$t", MON, sched, sched + err, basis, ObservationSource.MANUAL, t)

class DelayModelTest {
    @Test fun noObservationsMeansNoCorrection() {
        val e = DelayModel(emptyList(), Basis.SCHEDULED).estimate("2", "s1", MON, 8 * 3600)
        assertEquals(0, e.deltaSec); assertEquals(0, e.nRoute)
    }

    @Test fun learnsConsistentDelayWithShrinkage() {
        val o = (1..20).map { obs(120, it.toLong()) }
        val e = DelayModel(o, Basis.SCHEDULED).estimate("2", "s1", MON, 8 * 3600)
        assertTrue("delta=${e.deltaSec}", e.deltaSec in 100..120)
        assertEquals(20, e.nLeaf)
    }

    @Test fun fewObservationsAreShrunkTowardsZero() {
        val e = DelayModel(listOf(obs(300, 1)), Basis.SCHEDULED).estimate("2", "s1", MON, 8 * 3600)
        assertTrue(e.deltaSec in 1..299) // no se fía de una sola muestra
    }

    @Test fun robustToOutliersAndIgnoresImplausible() {
        val o = (1..9).map { obs(60, it.toLong()) } + obs(1700, 10) + obs(9000, 11) // 9000 s se descarta
        val m = DelayModel(o, Basis.SCHEDULED)
        assertEquals(10, m.size)
        assertTrue(m.estimate("2", "s1", MON, 8 * 3600).deltaSec in 40..75)
    }

    @Test fun otherRouteFallsBackToGlobalNotLeaf() {
        val o = (1..20).map { obs(120, it.toLong()) }
        val e = DelayModel(o, Basis.SCHEDULED).estimate("99", "zz", MON, 8 * 3600)
        assertEquals(0, e.nRoute); assertTrue(e.deltaSec > 0) // hereda el sesgo global, sin datos propios
    }

    @Test fun basesNeverMix() {
        val o = (1..20).map { obs(300, it.toLong(), basis = Basis.REFERENCE) }
        assertEquals(0, DelayModel(o, Basis.SCHEDULED).size)
        assertEquals(0, DelayModel(o, Basis.SCHEDULED).estimate("2", "s1", MON, 8 * 3600).deltaSec)
    }
}

class EvaluationTest {
    @Test fun tooFewObservationsGivesNoEvaluation() {
        assertNull(Evaluation.walkForward((1..5).map { obs(60, it.toLong()) }, Basis.SCHEDULED))
    }

    @Test fun systematicDelayIsLearnedAndEnables() {
        val rnd = java.util.Random(7)
        val o = (1..60).map { obs(150 + rnd.nextInt(41) - 20, it.toLong()) } // retraso sintético: solo en tests
        val ev = Evaluation.walkForward(o, Basis.SCHEDULED)!!
        assertTrue(ev.maeModel < ev.maeSchedule * 0.5)
        assertTrue(ev.enabled)
        assertTrue(abs(ev.biasModel) < abs(ev.biasSchedule))
    }

    @Test fun pureNoiseDoesNotEnable() {
        val rnd = java.util.Random(11)
        val o = (1..80).map { obs(rnd.nextInt(241) - 120, it.toLong()) } // ruido sin sesgo
        val ev = Evaluation.walkForward(o, Basis.SCHEDULED)!!
        assertFalse("model=${ev.maeModel} sched=${ev.maeSchedule}", ev.enabled)
    }

    @Test fun evaluationIsChronologicalNoLeakage() {
        // El primer salto de nivel solo puede conocerse tras verlo: las 8 primeras (precisamente) no se predicen con futuro.
        val o = (1..10).map { obs(0, it.toLong()) } + (11..20).map { obs(600, it.toLong()) }
        val ev = Evaluation.walkForward(o, Basis.SCHEDULED)!!
        assertTrue(ev.maeModel > 100) // el cambio de régimen cuesta error: no hay fuga de información futura
    }
}

class BoardingDetectorTest {
    private val lat = 37.88; private val lon = -4.78
    private fun at(metersEast: Double) = lon + metersEast / (111_320.0 * Math.cos(Math.toRadians(lat)))
    private fun det() = BoardingDetector("s1", lat, lon)

    private fun feed(d: BoardingDetector, samples: List<Pair<Double, Long>>): BoardingEvent? {
        var ev: BoardingEvent? = null
        for ((m, t) in samples) d.onSample(LocationSample(t, lat, at(m), 5f))?.let { ev = ev ?: it }
        return ev
    }

    private val waiting = (0..40).map { 5.0 to it * 1000L }          // 40 s esperando junto a la parada
    private fun run(startMs: Long, mps: Double, seconds: Int, from: Double = 5.0) = (1..seconds).map { from + mps * it to startMs + it * 1000L }

    @Test fun waitThenBusSpeedDetectsBoarding() {
        val ev = feed(det(), waiting + run(40_000, 9.0, 20))
        assertNotNull(ev); assertEquals("s1", ev!!.stopId)
        assertTrue(ev.movedAtMs in 40_000..46_000)
    }

    @Test fun receiverReportingZeroSpeedStillDetectsFromPositions() {
        val d = det(); var ev: BoardingEvent? = null
        val samples = waiting.map { it.first to it.second } + run(40_000, 10.0, 20)
        for ((m, t) in samples) d.onSample(LocationSample(t, lat, at(m), 5f, speedMps = 0f))?.let { ev = ev ?: it }
        assertNotNull(ev)
    }

    @Test fun gpsJitterWhileWaitingIsNotMovement() {
        val d = det(); val rnd = java.util.Random(5); var ev: BoardingEvent? = null
        for (t in 0..120) d.onSample(LocationSample(t * 1000L, lat, at(rnd.nextDouble() * 6 - 3), 8f, speedMps = 0f))?.let { ev = ev ?: it }
        assertNull(ev)
    }

    @Test fun walkingAwayIsNotBoarding() = assertNull(feed(det(), waiting + run(40_000, 1.4, 60)))

    @Test fun cyclingIsNotBoarding() = assertNull(feed(det(), waiting + run(40_000, 5.5, 60)))

    @Test fun passingByInACarWithoutWaitingIsNotBoarding() = assertNull(feed(det(), run(0, 12.0, 30, from = -200.0)))

    @Test fun poorAccuracyFixesAreIgnored() {
        val d = det()
        val bad = (0..60).map { LocationSample(it * 1000L, lat, at(it * 10.0), 200f) }
        assertNull(bad.firstNotNullOfOrNull { d.onSample(it) })
    }
}
