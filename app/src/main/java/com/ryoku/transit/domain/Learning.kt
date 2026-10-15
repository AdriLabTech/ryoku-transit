package com.ryoku.transit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

enum class ObservationSource { MANUAL, AUTO_CONFIRMED }

/**
 * Una llegada real observada frente a la hora que la app mostró.
 * Todos los segundos están en el mismo marco que [Arrival.scheduledSec] (día de servicio de hoy).
 */
data class Observation(
    val routeId: String,
    val direction: Int,
    val stopId: String,
    val tripId: String,
    val serviceDate: LocalDate,
    val scheduledSec: Int,
    val actualSec: Int,
    val basis: Basis,
    val source: ObservationSource,
    /** Instante local de registro (epoch ms). Ordena la evaluación cronológica. */
    val recordedAt: Long,
) {
    /** Retraso real sobre lo programado (positivo = tarda más). */
    val errorSec: Int get() = actualSec - scheduledSec
    val plausible: Boolean get() = errorSec in PLAUSIBLE_ERROR

    companion object {
        /** Se descartan por implausibles (probable viaje mal emparejado) errores fuera de −10 min … +30 min. */
        val PLAUSIBLE_ERROR = -600..1800
    }
}

enum class DayType { WEEKDAY, SATURDAY, SUNDAY;
    companion object {
        fun of(d: LocalDate) = when (d.dayOfWeek) { DayOfWeek.SATURDAY -> SATURDAY; DayOfWeek.SUNDAY -> SUNDAY; else -> WEEKDAY }
    }
}

/** Franja horaria (según la hora programada) y tipo de día. */
data class Bucket(val dayType: DayType, val part: Int) {
    companion object {
        fun of(date: LocalDate, scheduledSec: Int): Bucket {
            val h = (scheduledSec / 3600) % 24
            val part = when { h < 7 -> 0; h < 10 -> 1; h < 14 -> 2; h < 17 -> 3; h < 21 -> 4; else -> 5 }
            return Bucket(DayType.of(date), part)
        }
    }
}

data class DelayEstimate(val deltaSec: Int, val nRoute: Int, val nLeaf: Int)

/**
 * Corrección de retraso aprendida de observaciones propias. Estimación robusta (mediana) con contracción
 * jerárquica hacia niveles más generales: global → línea → línea×franja → línea×franja×parada.
 * Entrenar y usar siempre sobre una misma [Basis]: el error contra un horario de referencia no se mezcla con el vigente.
 */
class DelayModel(observations: List<Observation>, private val basis: Basis, private val shrink: Double = 4.0) {
    private val obs = observations.filter { it.basis == basis && it.plausible }
    private val byRoute = obs.groupBy { it.routeId }
    private val byBucket = obs.groupBy { Triple(it.routeId, Bucket.of(it.serviceDate, it.scheduledSec), null as String?) }
    private val byLeaf = obs.groupBy { Triple(it.routeId, Bucket.of(it.serviceDate, it.scheduledSec), it.stopId as String?) }
    private val global = median(obs)

    val size get() = obs.size

    fun estimate(routeId: String, stopId: String, date: LocalDate, scheduledSec: Int): DelayEstimate {
        val b = Bucket.of(date, scheduledSec)
        val r = byRoute[routeId].orEmpty()
        val m = byBucket[Triple(routeId, b, null)].orEmpty()
        val l = byLeaf[Triple(routeId, b, stopId)].orEmpty()
        // El nivel global también se contrae hacia 0 (sin datos no hay corrección).
        var est = global * obs.size / (obs.size + shrink)
        est = blend(r, est); est = blend(m, est); est = blend(l, est)
        return DelayEstimate(est.toInt(), r.size, l.size)
    }

    private fun blend(group: List<Observation>, parent: Double): Double {
        if (group.isEmpty()) return parent
        val n = group.size
        return (n * median(group) + shrink * parent) / (n + shrink)
    }

    private fun median(g: List<Observation>): Double {
        if (g.isEmpty()) return 0.0
        val s = g.map { it.errorSec }.sorted()
        return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}

data class Evaluation(
    val n: Int,
    val maeSchedule: Double, val maeModel: Double,
    val rmseSchedule: Double, val rmseModel: Double,
    val biasSchedule: Double, val biasModel: Double,
) {
    /** Solo se activa el ajuste si, con evaluación cronológica, mejora al horario al menos un 5 % en MAE. */
    val enabled: Boolean get() = n >= MIN_EVAL && maeModel <= maeSchedule * 0.95

    companion object {
        const val MIN_TRAIN = 5
        const val MIN_EVAL = 8
        private const val MAX_OBS = 1000

        /** Evaluación «walk-forward»: cada observación se predice con un modelo entrenado solo con las anteriores. */
        fun walkForward(all: List<Observation>, basis: Basis): Evaluation? {
            val seq = all.filter { it.basis == basis && it.plausible }.sortedBy { it.recordedAt }.takeLast(MAX_OBS)
            if (seq.size <= MIN_TRAIN) return null
            var n = 0; var aS = 0.0; var aM = 0.0; var qS = 0.0; var qM = 0.0; var bS = 0.0; var bM = 0.0
            for (i in MIN_TRAIN until seq.size) {
                val o = seq[i]
                val pred = DelayModel(seq.subList(0, i), basis).estimate(o.routeId, o.stopId, o.serviceDate, o.scheduledSec).deltaSec
                val eS = o.errorSec.toDouble(); val eM = (o.errorSec - pred).toDouble()
                n++; aS += abs(eS); aM += abs(eM); qS += eS * eS; qM += eM * eM; bS += eS; bM += eM
            }
            return Evaluation(n, aS / n, aM / n, sqrt(qS / n), sqrt(qM / n), bS / n, bM / n)
        }
    }
}
