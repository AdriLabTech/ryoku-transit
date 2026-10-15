package com.ryoku.transit.domain

import java.time.LocalDate

class WeeklyService(val days: BooleanArray, val start: LocalDate, val end: LocalDate) {
    init { require(days.size == 7) }
    fun runsOn(date: LocalDate) =
        !date.isBefore(start) && !date.isAfter(end) && days[date.dayOfWeek.ordinal]
}

/** Interpreta calendar.txt + calendar_dates.txt. exception_type 1 = añade, 2 = quita. */
class ServiceCalendar(
    private val weekly: Map<String, WeeklyService>,
    private val exceptions: Map<String, Map<LocalDate, Int>>,
) {
    private val cache = HashMap<LocalDate, Set<String>>()

    fun isActive(serviceId: String, date: LocalDate): Boolean {
        when (exceptions[serviceId]?.get(date)) {
            1 -> return true
            2 -> return false
        }
        return weekly[serviceId]?.runsOn(date) == true
    }

    fun activeServices(date: LocalDate): Set<String> = cache.getOrPut(date) {
        (weekly.keys + exceptions.keys).filterTo(HashSet()) { isActive(it, date) }
    }

    fun hasService(date: LocalDate) = activeServices(date).isNotEmpty()

    companion object {
        fun parseDow(bits: String): BooleanArray {
            require(bits.length == 7 && bits.all { it == '0' || it == '1' }) { "dow inválido: $bits" }
            return BooleanArray(7) { bits[it] == '1' }
        }
    }
}
