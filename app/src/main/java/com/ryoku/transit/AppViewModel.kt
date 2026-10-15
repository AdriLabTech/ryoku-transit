package com.ryoku.transit

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryoku.transit.data.*
import com.ryoku.transit.domain.*
import com.ryoku.transit.ui.ThemeMode
import kotlinx.coroutines.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val clock: Clock = Clock.system(zone)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val favs = Favorites(app)

    var schedule by mutableStateOf<SqliteSchedule?>(null); private set
    var calculator by mutableStateOf<ArrivalCalculator?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var favorites by mutableStateOf(favs.stops()); private set
    var notices by mutableStateOf<NoticesState>(NoticesState.Loading); private set
    var now by mutableStateOf(ZonedDateTime.now(clock)); private set
    var allowReference by mutableStateOf(prefs.getBoolean("allowReference", true)); private set
    var theme by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "GRAFITO")!!) }.getOrDefault(ThemeMode.GRAFITO)); private set

    private val noticesSource = NoticesSource(java.io.File(app.cacheDir, "notices.xml"))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = SqliteSchedule(TransitDb.open(app))
                // Fuerza la carga perezosa fuera del hilo principal.
                val feed = s.feed; s.calendar; s.routes(); s.searchStops("zz")
                calculator = ArrivalCalculator(s, s.calendar, feed, zone)
                schedule = s
            } catch (e: Exception) { loadError = e.message ?: e.javaClass.simpleName }
        }
        refreshNotices()
        viewModelScope.launch { while (isActive) { now = ZonedDateTime.now(clock); delay(15_000) } }
    }

    fun refreshNotices() {
        notices = NoticesState.Loading
        viewModelScope.launch(Dispatchers.IO) { notices = noticesSource.fetch() }
    }

    fun toggleFavorite(stopId: String) { favorites = favs.toggle(stopId) }
    fun updateAllowReference(v: Boolean) { allowReference = v; prefs.edit().putBoolean("allowReference", v).apply() }
    fun updateTheme(t: ThemeMode) { theme = t; prefs.edit().putString("theme", t.name).apply() }

    fun arrivals(stopId: String, limit: Int = 30): ArrivalCalculator.Result? =
        calculator?.next(stopId, now, allowReference, limit = limit)

    fun nowInstant(): Instant = now.toInstant()
}
