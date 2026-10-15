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
import java.time.Duration

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

    // ── Aprendizaje ──
    private val store = ObservationStore(app)
    private val learnPrefs = app.getSharedPreferences(WaitingService.PREFS, Context.MODE_PRIVATE)
    // El servicio escribe el evento pendiente en otro componente: se observa para mostrar la tarjeta sin esperar a onResume.
    private val pendingListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshPending() }

    var observations by mutableStateOf<List<Observation>>(emptyList()); private set
    var evaluations by mutableStateOf<Map<Basis, Evaluation?>>(emptyMap()); private set
    var adjustEnabled by mutableStateOf(prefs.getBoolean("adjust", true)); private set
    var pending by mutableStateOf<BoardingEvent?>(null); private set
    private var models: Map<Basis, DelayModel> = emptyMap()
    /** Claves ya anotadas: `trip|stop|fecha|base`. */
    var observedKeys by mutableStateOf<Set<String>>(emptySet()); private set

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
        reloadLearning()
        refreshPending()
        learnPrefs.registerOnSharedPreferenceChangeListener(pendingListener)
        viewModelScope.launch { while (isActive) { now = ZonedDateTime.now(clock); delay(15_000) } }
    }

    fun refreshNotices() {
        notices = NoticesState.Loading
        viewModelScope.launch(Dispatchers.IO) { notices = noticesSource.fetch() }
    }

    fun toggleFavorite(stopId: String) { favorites = favs.toggle(stopId) }
    fun updateAllowReference(v: Boolean) { allowReference = v; prefs.edit().putBoolean("allowReference", v).apply() }
    fun updateTheme(t: ThemeMode) { theme = t; prefs.edit().putString("theme", t.name).apply() }

    /** Llegadas con ajuste aprendido (si está validado) y con margen de 10 min hacia atrás para poder anotar retrasos. */
    fun arrivals(stopId: String, limit: Int = 30, lookback: Duration = Duration.ofMinutes(10)): ArrivalCalculator.Result? {
        val r = calculator?.next(stopId, now, allowReference, limit = limit, lookback = lookback) ?: return null
        if (r !is ArrivalCalculator.Result.Ok) return r
        val adjusted = r.arrivals.map { a ->
            val m = models[a.basis]
            if (!adjustEnabled || m == null) return@map a
            val e = m.estimate(a.routeId, stopId, a.serviceDate, a.scheduledSec)
            val dm = Math.round(e.deltaSec / 60.0).toInt()
            if (dm == 0 || e.nRoute < MIN_ROUTE_OBS) a
            else a.copy(adjustedSec = a.scheduledSec + dm * 60, adjustedMinutes = a.minutes + dm, adjustSupport = e.nRoute)
        }
        return r.copy(arrivals = adjusted.sortedWith(compareBy({ it.effectiveSec }, { it.routeShortName })).filter { (it.adjustedMinutes ?: it.minutes) >= -10 })
    }

    fun key(a: Arrival, stopId: String) = "${a.tripId}|$stopId|${a.serviceDate}|${a.basis}"

    /** Anota que el autobús de [a] llegó hace [minutesAgo] minutos (0 = ahora). */
    fun recordArrival(a: Arrival, stopId: String, minutesAgo: Int) {
        val calc = calculator ?: return
        val actual = calc.serviceSeconds(now) - minutesAgo * 60
        persist(Observation(a.routeId, a.direction, stopId, a.tripId, a.serviceDate, a.scheduledSec, actual, a.basis, ObservationSource.MANUAL, System.currentTimeMillis()))
    }

    private fun persist(o: Observation) {
        viewModelScope.launch(Dispatchers.IO) { store.save(o); reloadLearningNow() }
    }

    fun reloadLearning() { viewModelScope.launch(Dispatchers.IO) { reloadLearningNow() } }

    private fun reloadLearningNow() {
        val all = store.all()
        val ev = Basis.entries.associateWith { Evaluation.walkForward(all, it) }
        models = Basis.entries.filter { ev[it]?.enabled == true }.associateWith { DelayModel(all, it) }
        observations = all
        evaluations = ev
        observedKeys = all.map { "${it.tripId}|${it.stopId}|${it.serviceDate}|${it.basis}" }.toSet()
    }

    override fun onCleared() { learnPrefs.unregisterOnSharedPreferenceChangeListener(pendingListener) }

    fun updateAdjust(v: Boolean) { adjustEnabled = v; prefs.edit().putBoolean("adjust", v).apply() }

    fun clearObservations() { viewModelScope.launch(Dispatchers.IO) { store.clear(); reloadLearningNow() } }

    // ── Subida detectada por el servicio ──
    fun refreshPending() {
        val id = learnPrefs.getString("pending_stop", null)
        pending = if (id == null) null else BoardingEvent(id, learnPrefs.getLong("pending_at", 0L))
    }

    fun dismissPending() { learnPrefs.edit().remove("pending_stop").remove("pending_at").apply(); pending = null }

    /** Salidas programadas en ±15 min de la subida detectada (candidatas a «la línea que cogí»). */
    fun boardingCandidates(ev: BoardingEvent): List<Arrival> {
        val calc = calculator ?: return emptyList()
        val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ev.movedAtMs), zone)
        val r = calc.next(ev.stopId, at.minusMinutes(15), allowReference, horizon = Duration.ofMinutes(30), limit = 60)
        return (r as? ArrivalCalculator.Result.Ok)?.arrivals.orEmpty()
    }

    fun confirmBoarding(ev: BoardingEvent, routeId: String) {
        val calc = calculator ?: return
        val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ev.movedAtMs), zone)
        val actual = calc.serviceSeconds(at)
        val a = boardingCandidates(ev).filter { it.routeId == routeId }.minByOrNull { Math.abs(it.scheduledSec - actual) }
        if (a != null) persist(Observation(a.routeId, a.direction, ev.stopId, a.tripId, a.serviceDate, a.scheduledSec, actual, a.basis, ObservationSource.AUTO_CONFIRMED, System.currentTimeMillis()))
        dismissPending()
    }



    fun nowInstant(): Instant = now.toInstant()

    companion object { const val MIN_ROUTE_OBS = 3 }
}
