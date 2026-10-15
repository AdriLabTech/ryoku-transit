package com.ryoku.transit.domain

import kotlin.math.cos
import kotlin.math.hypot

data class LocationSample(val timeMs: Long, val lat: Double, val lon: Double, val accuracyM: Float, val speedMps: Float? = null)

/** El usuario esperó en la parada y luego se movió a velocidad de vehículo: probablemente subió a un autobús. */
data class BoardingEvent(val stopId: String, val movedAtMs: Long)

/**
 * Detector de «he subido a un autobús» a partir de posiciones, sin guardarlas.
 *
 * Reglas (documentadas en docs/LEARNING.md):
 *  1. Espera: ≥ [DWELL_MS] dentro de [ZONE_M] m de la parada a menos de [WAIT_SPEED] m/s.
 *  2. Salida: tras esa espera, la velocidad llega a ≥ [BUS_SPEED] m/s (≈ 25 km/h; caminar ≈ 1,5, bici ≈ 5–6) y se mantiene [SUSTAIN_MS].
 *  3. La hora del evento es el primer instante en que se superó [MOVE_SPEED] tras la espera (≈ salida del bus de la parada).
 * Posiciones con precisión peor que [MAX_ACC] m se ignoran. La confirmación humana filtra el resto de falsos positivos.
 */
class BoardingDetector(private val stopId: String, private val stopLat: Double, private val stopLon: Double) {
    private var dwellStart: Long? = null
    private var waited = false
    private var lastInZoneMs = 0L
    private val window = ArrayDeque<LocationSample>()
    private var moveStart: Long? = null
    private var fastSince: Long? = null

    fun onSample(s: LocationSample): BoardingEvent? {
        if (s.accuracyM > MAX_ACC) return null
        window.addLast(s)
        while (window.size > 1 && s.timeMs - window.first().timeMs > WINDOW_MS) window.removeFirst()
        // Velocidad = máx(reportada, desplazamiento en ventana − precisión): robusto a receptores que dan velocidad 0 y al ruido del GPS.
        val o = window.first()
        val dt = (s.timeMs - o.timeMs) / 1000.0
        val derived = if (dt >= 1.0) maxOf(0.0, dist(o.lat, o.lon, s.lat, s.lon) - maxOf(o.accuracyM, s.accuracyM)) / dt else 0.0
        val speed = maxOf(s.speedMps?.takeIf { it >= 0f }?.toDouble() ?: 0.0, derived)
        val inZone = dist(stopLat, stopLon, s.lat, s.lon) <= ZONE_M

        if (!waited) {
            if (inZone && speed < WAIT_SPEED) {
                val d0 = dwellStart ?: s.timeMs.also { dwellStart = it }
                if (s.timeMs - d0 >= DWELL_MS) waited = true
            } else dwellStart = null
            lastInZoneMs = if (inZone) s.timeMs else lastInZoneMs
            return null
        }
        if (inZone) lastInZoneMs = s.timeMs
        // Ya esperó: busca arranque y velocidad de vehículo sostenida.
        if (speed >= MOVE_SPEED) { if (moveStart == null) moveStart = s.timeMs } else { moveStart = null; fastSince = null }
        if (speed >= BUS_SPEED) {
            val f = fastSince ?: s.timeMs.also { fastSince = it }
            val ms = moveStart
            if (s.timeMs - f >= SUSTAIN_MS && ms != null && ms - lastInZoneMs <= MAX_GAP_MS) return BoardingEvent(stopId, ms)
        } else fastSince = null
        return null
    }

    private fun dist(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val k = 111_320.0
        return hypot((la2 - la1) * k, (lo2 - lo1) * k * cos(Math.toRadians((la1 + la2) / 2)))
    }

    companion object {
        const val ZONE_M = 60.0
        const val DWELL_MS = 20_000L
        const val WAIT_SPEED = 1.5
        const val MOVE_SPEED = 3.0
        const val BUS_SPEED = 7.0
        const val SUSTAIN_MS = 8_000L
        const val MAX_GAP_MS = 120_000L
        const val MAX_ACC = 50f
        const val WINDOW_MS = 5_000L
    }
}
