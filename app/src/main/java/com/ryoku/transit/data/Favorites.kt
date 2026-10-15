package com.ryoku.transit.data

import android.content.Context

/** Paradas favoritas en SharedPreferences. Solo identificadores; nada personal. */
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun stops(): List<String> = prefs.getString("stops", "").orEmpty().split(',').filter { it.isNotEmpty() }

    fun toggle(stopId: String): List<String> {
        val cur = stops().toMutableList()
        if (!cur.remove(stopId)) cur.add(0, stopId)
        prefs.edit().putString("stops", cur.joinToString(",")).apply()
        return cur
    }
}
