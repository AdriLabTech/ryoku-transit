package com.ryoku.transit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ryoku.transit.AppViewModel
import com.ryoku.transit.WaitingService
import com.ryoku.transit.data.*
import com.ryoku.transit.domain.Basis
import com.ryoku.transit.domain.Evaluation
import com.ryoku.transit.domain.ObservationSource
import com.ryoku.transit.domain.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val PAD = Modifier.padding(horizontal = Space.x4)

@Composable
fun ScreenHeader(index: Int, title: String, onStatus: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Row(PAD.fillMaxWidth().padding(top = Space.x4, bottom = Space.x3), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(Size.control).clickable(role = Role.Button, onClickLabel = "Volver", onClick = onBack).semantics { contentDescription = "Volver" }, contentAlignment = Alignment.CenterStart) { RText("←", Ryoku.t.h1) }
        } else { BrandDisc(); Spacer(Modifier.width(Space.x2)) }
        Column(Modifier.weight(1f)) {
            SectionLabel(index, if (onBack == null) "RYOKU TRANSIT" else "DETALLE")
            RText(title, Ryoku.t.h1, maxLines = 2, modifier = Modifier.semantics { heading() })
        }
        if (onStatus != null) Box(
            Modifier.heightIn(min = Size.control).clickable(role = Role.Button, onClick = onStatus).padding(horizontal = Space.x2),
            contentAlignment = Alignment.Center,
        ) { RText("ESTADO", Ryoku.t.label, Ryoku.c.secondary) }
    }
}

@Composable
fun Loading(vm: AppViewModel, content: @Composable (SqliteSchedule) -> Unit) {
    val s = vm.schedule
    when {
        vm.loadError != null -> Banner(Tone.Error, "No se pudo abrir la base de datos", vm.loadError!!, PAD)
        s == null -> LoadingBlock("Cargando red de transporte")
        else -> content(s)
    }
}

/** Cobertura temporal de los horarios: vigente, de referencia o inexistente. */
@Composable
fun CoverageNote(vm: AppViewModel, result: ArrivalCalculator.Result?, s: SqliteSchedule) {
    val feed = s.feed
    when (result) {
        is ArrivalCalculator.Result.Ok -> if (result.basis == Basis.REFERENCE) Banner(
            Tone.Warn, "Horario de referencia · no es de hoy",
            "El GTFS publicado solo cubre hasta el ${feed.end.format(DMY)}. Se muestran las salidas del ${result.serviceDate.format(DMY)} (mismo día de la semana). Las horas reales pueden diferir.",
        ) else StatusPill(Tone.Ok, "Horario vigente hasta el ${feed.end.format(DMY)}")
        is ArrivalCalculator.Result.NoCoverage -> Banner(
            Tone.Error, if (result.status == FeedStatus.EXPIRED) "Horarios caducados" else "Horarios aún no vigentes",
            "El feed cubre del ${feed.start.format(DMY)} al ${feed.end.format(DMY)}. Activa el horario de referencia en ESTADO para ver una salida orientativa.",
        )
        null -> {}
    }
}

@Composable
fun LiveNote() = RText("Sin posición ni predicción en vivo: no hay fuente de tiempo real accesible (ver ESTADO).", Ryoku.t.small, Ryoku.c.inkMuted)

// ─────────────── Paradas ───────────────

@Composable
fun StopsScreen(vm: AppViewModel, onStop: (String) -> Unit, onStatus: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var nearby by remember { mutableStateOf<List<Pair<Stop, Int>>?>(null) }
    var locMsg by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.schedule?.let { s -> locate(ctx, s).let { nearby = it.first; locMsg = it.second } } else locMsg = "Sin permiso de ubicación. Puedes buscar por nombre o código."
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(0, "Paradas", onStatus)
        Loading(vm) { s ->
            Column(PAD.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SearchField(q, { q = it; nearby = null }, "Buscar parada por nombre o código", visibleHint = "Nombre o código")
                GhostButton("Paradas cerca de mí", onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        locate(ctx, s).let { nearby = it.first; locMsg = it.second }
                    else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }, Modifier.fillMaxWidth())
            }
            val results = if (q.isNotBlank()) s.searchStops(q).map { it to -1 } else nearby.orEmpty()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                if (results.isEmpty()) item {
                    when {
                        q.isNotBlank() -> EmptyState("Sin resultados", "Ninguna parada coincide con «$q».")
                        locMsg != null -> EmptyState("Ubicación", locMsg!!)
                        else -> EmptyState("Busca una parada", "Escribe el nombre de la calle o el número que figura en el poste. La app tiene ${s.allStops().size} paradas.")
                    }
                }
                items(results, key = { it.first.id }) { (stop, dist) -> StopCard(stop, dist, vm.favorites.contains(stop.id)) { onStop(stop.id) } }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun locate(ctx: Context, s: SqliteSchedule): Pair<List<Pair<Stop, Int>>?, String?> {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        ?: return null to "Sin ubicación reciente del dispositivo. Abre un mapa para obtenerla o busca por nombre."
    val out = s.allStops().map { st ->
        val r = FloatArray(1); android.location.Location.distanceBetween(loc.latitude, loc.longitude, st.lat, st.lon, r); st to r[0].toInt()
    }.sortedBy { it.second }.take(10)
    return out to null
}

@Composable
fun StopCard(stop: Stop, distanceM: Int, favorite: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RText(stop.name, Ryoku.t.bodyBold, maxLines = 2)
                RText("Parada ${stop.code}" + if (distanceM >= 0) " · a ${distanceM} m" else "", Ryoku.t.small, Ryoku.c.inkMuted)
            }
            if (favorite) RText("★", Ryoku.t.h3, Ryoku.c.gold, Modifier.semantics { contentDescription = "Favorita" })
        }
    }
}

// ─────────────── Detalle de parada ───────────────

@Composable
fun StopDetailScreen(vm: AppViewModel, stopId: String, onBack: () -> Unit, onLine: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Loading(vm) { s ->
            val stop = s.stop(stopId)
            if (stop == null) { ScreenHeader(0, "Parada", onBack = onBack); EmptyState("Parada no encontrada", "El identificador $stopId no existe en el feed."); return@Loading }
            ScreenHeader(0, stop.name, onBack = onBack)
            val result = remember(vm.now, stopId, vm.allowReference) { vm.arrivals(stopId) }
            val lines = remember(stopId) { s.routesAtStop(stopId) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        Column(Modifier.weight(1f)) {
                            RText("Parada ${stop.code}", Ryoku.t.small, Ryoku.c.inkMuted)
                            Row(Modifier.padding(top = Space.x1), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                                lines.take(6).forEach { r -> RouteBadge(r.shortName, r.color, Modifier.clickable(role = Role.Button, onClickLabel = "Ver línea ${r.shortName}") { onLine(r.id) }) }
                            }
                        }
                        FavoriteToggle(vm.favorites.contains(stopId), { vm.toggleFavorite(stopId) })
                    }
                }
                item { Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) { Spacer(Modifier.height(Space.x1)); CoverageNote(vm, result, s); LiveNote(); WaitingControl(stop) } }
                item { SectionLabel(1, "Próximas salidas", Modifier.padding(top = Space.x2)) }
                when (result) {
                    is ArrivalCalculator.Result.Ok ->
                        if (result.arrivals.isEmpty()) item { EmptyState("Sin salidas en las próximas 3 horas", "No hay servicio programado en ese intervalo.") }
                        else items(result.arrivals, key = { "${it.tripId}-${it.scheduledSec}" }) { a -> ArrivalRow(a, vm.key(a, stopId) in vm.observedKeys, { ago -> vm.recordArrival(a, stopId, ago) }) }
                    else -> {}
                }
            }
        }
    }
}

/** Activa el modo «estoy esperando aquí»: detecta la subida al bus y pide confirmar la línea. */
@Composable
fun WaitingControl(stop: Stop) {
    val ctx = LocalContext.current
    val running by WaitingService.running.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val perms = buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) { WaitingService.start(ctx, stop.id, stop.name, stop.lat, stop.lon); msg = null }
        else msg = "Sin permiso de ubicación precisa: el modo automático no puede funcionar. Puedes anotar a mano."
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        if (running == stop.id) {
            StatusPill(Tone.Info, "Esperando aquí · máx. 25 min")
            GhostButton("Cancelar modo esperando", { WaitingService.stop(ctx) }, Modifier.fillMaxWidth())
        } else {
            GhostButton("Estoy esperando aquí (anotar mi viaje solo)", { launcher.launch(perms) }, Modifier.fillMaxWidth())
            RText("Usa tu ubicación mientras esté activo (máx. 25 min). No se guarda ni se envía: solo se anota la hora en que sales de la parada a velocidad de autobús, y tú confirmas la línea.", Ryoku.t.small, Ryoku.c.inkMuted)
        }
        msg?.let { RText(it, Ryoku.t.small, Ryoku.c.error) }
    }
}

// ─────────────── Líneas ───────────────

@Composable
fun LinesScreen(vm: AppViewModel, onLine: (String) -> Unit, onStatus: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(0, "Líneas", onStatus)
        Loading(vm) { s ->
            val routes = remember { s.routes() }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                items(routes, key = { it.id }) { r ->
                    Card(onClick = { onLine(r.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                            RouteBadge(r.shortName, r.color)
                            RText(r.longName.substringAfter(": "), Ryoku.t.body, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LineDetailScreen(vm: AppViewModel, routeId: String, onBack: () -> Unit, onStop: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Loading(vm) { s ->
            val route = s.route(routeId)
            val dirs = remember(routeId) { s.directions(routeId) }
            var di by rememberSaveable { mutableIntStateOf(0) }
            ScreenHeader(0, "Línea ${route?.shortName ?: routeId}", onBack = onBack)
            if (dirs.isEmpty()) { EmptyState("Sin viajes", "El feed no contiene viajes para esta línea."); return@Loading }
            val d = dirs[di.coerceIn(0, dirs.lastIndex)]
            val shapes = remember { s.allShapes() }
            val all = remember { s.allStops() }
            Row(PAD.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                dirs.forEachIndexed { i, dd -> GhostButton("→ ${dd.headsign}", { di = i }, Modifier.weight(1f), selected = dd === d) }
            }
            Box(Modifier.padding(Space.x4).fillMaxWidth().height(200.dp).background(Ryoku.c.paper)) {
                NetworkMap(shapes, all, highlight = d.shape, highlightStops = d.stops, fit = d.shape.ifEmpty { d.stops.map { LatLon(it.lat, it.lon) } },
                    description = "Mapa esquemático de la línea ${route?.shortName} hacia ${d.headsign}, con ${d.stops.size} paradas. La lista inferior contiene las mismas paradas.")
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.x4, vertical = Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                item { SectionLabel(1, "${d.stops.size} paradas") }
                items(d.stops.size, key = { "${d.direction}-$it" }) { i ->
                    val st = d.stops[i]
                    Row(Modifier.fillMaxWidth().heightIn(min = Size.control).clickable(role = Role.Button) { onStop(st.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        RText("%02d".format(i + 1), Ryoku.t.small, Ryoku.c.inkMuted, Modifier.width(24.dp))
                        RText(st.name, Ryoku.t.body, maxLines = 1, modifier = Modifier.weight(1f))
                        RText(st.code, Ryoku.t.small, Ryoku.c.inkFaint)
                    }
                }
            }
        }
    }
}

// ─────────────── Mapa ───────────────

@Composable
fun MapScreen(vm: AppViewModel, onStop: (String) -> Unit, onList: () -> Unit, onStatus: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(0, "Mapa", onStatus)
        Loading(vm) { s ->
            val shapes = remember { s.allShapes() }
            val stops = remember { s.allStops() }
            var sel by remember { mutableStateOf<Stop?>(null) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                NetworkMap(shapes, stops, selected = sel, onStopTap = { sel = it },
                    description = "Mapa esquemático de la red con ${stops.size} paradas. Toca una parada para ver sus datos. Alternativa accesible: la lista de paradas en la pestaña Paradas.")
                Column(Modifier.align(Alignment.TopEnd).padding(Space.x2)) { GhostButton("Ver lista", onList) }
            }
            Column(PAD.fillMaxWidth().padding(vertical = Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                val st = sel
                if (st == null) RText("Toca una parada del mapa. Sin posiciones de autobuses: no hay datos en vivo.", Ryoku.t.small, Ryoku.c.inkMuted)
                else StopCard(st, -1, vm.favorites.contains(st.id)) { onStop(st.id) }
            }
        }
    }
}

// ─────────────── Favoritas ───────────────

@Composable
fun FavoritesScreen(vm: AppViewModel, onStop: (String) -> Unit, onStatus: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(0, "Favoritas", onStatus)
        Loading(vm) { s ->
            if (vm.favorites.isEmpty()) EmptyState("Aún no hay favoritas", "Abre una parada y pulsa ☆ para tenerla aquí con sus próximas salidas.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                items(vm.favorites, key = { it }) { id ->
                    val stop = s.stop(id) ?: return@items
                    val res = remember(vm.now, id, vm.allowReference) { vm.arrivals(id, 3, java.time.Duration.ZERO) }
                    Card(onClick = { onStop(id) }) {
                        RText(stop.name, Ryoku.t.bodyBold, maxLines = 2)
                        RText("Parada ${stop.code}", Ryoku.t.small, Ryoku.c.inkMuted)
                        Spacer(Modifier.height(Space.x2))
                        when (res) {
                            is ArrivalCalculator.Result.Ok -> if (res.arrivals.isEmpty()) RText("Sin salidas próximas", Ryoku.t.small, Ryoku.c.inkMuted) else res.arrivals.forEach {
                                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                                    RText(if (it.minutes == 0) "ya" else "${it.minutes} min", Ryoku.t.h3, if (it.basis == Basis.REFERENCE) Ryoku.c.inkDim else Ryoku.c.ink, Modifier.width(72.dp))
                                    RouteBadge(it.routeShortName, it.routeColor)
                                    RText(it.headsign + if (it.basis == Basis.REFERENCE) "  · REF" else "", Ryoku.t.small, Ryoku.c.inkDim, maxLines = 1)
                                }
                            }
                            else -> RText("Horarios no vigentes (ver ESTADO)", Ryoku.t.small, Ryoku.c.error)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────── Avisos ───────────────

@Composable
fun NoticesScreen(vm: AppViewModel, onStatus: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(0, "Avisos", onStatus)
        when (val n = vm.notices) {
            NoticesState.Loading -> LoadingBlock("Cargando avisos")
            is NoticesState.Failed -> Column(PAD, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Banner(Tone.Error, "No se pudieron cargar los avisos", "${n.reason}. Comprueba la conexión.")
                GhostButton("Reintentar", vm::refreshNotices)
            }
            is NoticesState.Loaded -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        RText("Fuente: feed RSS de aucorsa.es. Son noticias y avisos de la web: no están asociados a líneas ni a paradas.", Ryoku.t.small, Ryoku.c.inkMuted)
                        if (n.stale) StatusPill(Tone.Warn, "Sin conexión: copia guardada") else StatusPill(Tone.Ok, "Recibido ahora")
                        RText(freshnessLabel(n.fetchedAt, vm.nowInstant()), Ryoku.t.small, Ryoku.c.inkMuted)
                        GhostButton("Actualizar", vm::refreshNotices)
                    }
                }
                items(n.notices, key = { it.link }) { it ->
                    Card(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it.link))) } }) {
                        RText(it.published?.atZone(ZoneId.of("Europe/Madrid"))?.format(DMY) ?: "Sin fecha", Ryoku.t.label, Ryoku.c.inkMuted)
                        RText(it.title, Ryoku.t.bodyBold, modifier = Modifier.padding(vertical = Space.x1))
                        RText(it.summary, Ryoku.t.small, Ryoku.c.inkDim, maxLines = 3)
                    }
                }
            }
        }
    }
}

// ─────────────── Estado (diagnóstico) ───────────────

@Composable
fun StatusScreen(vm: AppViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(0, "Estado de los datos", onBack = onBack)
        Loading(vm) { s ->
            val feed = s.feed
            val today = vm.now.toLocalDate()
            Column(PAD.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionLabel(1, "Horarios (GTFS)")
                Card {
                    Kv("Fuente", feed.source)
                    Kv("Vigencia", "${feed.start.format(DMY)} – ${feed.end.format(DMY)}")
                    Kv("Hoy", today.format(DMY))
                    Kv("Importado", feed.builtAt.substringBefore('T'))
                    Kv("SHA-256", feed.sha256.take(12) + "…")
                    Spacer(Modifier.height(Space.x2))
                    when (feed.status(today)) {
                        FeedStatus.VALID -> StatusPill(Tone.Ok, "Feed vigente")
                        FeedStatus.EXPIRED -> StatusPill(Tone.Error, "Feed caducado")
                        FeedStatus.NOT_YET_VALID -> StatusPill(Tone.Warn, "Feed futuro")
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = Size.control), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RText("Usar horario de referencia", Ryoku.t.bodyBold)
                        RText("Si el feed no cubre hoy, muestra un día equivalente del feed con la etiqueta REF.", Ryoku.t.small, Ryoku.c.inkMuted)
                    }
                    GhostButton(if (vm.allowReference) "Sí" else "No", { vm.updateAllowReference(!vm.allowReference) }, selected = vm.allowReference)
                }
                SectionLabel(2, "Capacidades")
                Card {
                    Cap("Red de líneas y paradas", Tone.Ok, "GTFS importado")
                    Cap("Llegadas programadas", if (feed.status(today) == FeedStatus.VALID) Tone.Ok else Tone.Warn, if (feed.status(today) == FeedStatus.VALID) "vigentes" else "solo referencia")
                    Cap("Avisos del servicio", Tone.Info, "RSS de la web, sin líneas")
                    Cap("Posición de autobuses", Tone.Error, "sin fuente accesible")
                    Cap("Predicción en vivo", Tone.Error, "sin fuente accesible")
                    Cap("Ocupación", Tone.Error, "sin fuente accesible")
                }
                RText("Detalle y motivos en docs/DATA_SOURCES.md. Pendiente: respuesta de AUCORSA a la solicitud de acceso a su servicio web.", Ryoku.t.small, Ryoku.c.inkMuted)
                SectionLabel(3, "Aprendizaje")
                LearningCard(vm)
                SectionLabel(4, "Tema")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    GhostButton("Grafito", { vm.updateTheme(ThemeMode.GRAFITO) }, selected = vm.theme == ThemeMode.GRAFITO)
                    GhostButton("Washi", { vm.updateTheme(ThemeMode.WASHI) }, selected = vm.theme == ThemeMode.WASHI)
                }
                Spacer(Modifier.height(Space.x6))
            }
        }
    }
}

@Composable
private fun LearningCard(vm: AppViewModel) {
    var confirmClear by remember { mutableStateOf(false) }
    Card {
        RText("Tus anotaciones comparan la hora mostrada con la llegada real. El ajuste solo se aplica si, evaluado en orden cronológico (cada anotación predicha con las anteriores), mejora al horario un 5 % o más.", Ryoku.t.small, Ryoku.c.inkMuted)
        Spacer(Modifier.height(Space.x2))
        Kv("Anotaciones", "${vm.observations.size} (${vm.observations.count { it.source == ObservationSource.MANUAL }} a mano, ${vm.observations.count { it.source == ObservationSource.AUTO_CONFIRMED }} auto)")
        for (b in Basis.entries) {
            val n = vm.observations.count { it.basis == b && it.plausible }
            val ev = vm.evaluations[b]
            val label = if (b == Basis.SCHEDULED) "Horario vigente" else "Horario de referencia"
            Spacer(Modifier.height(Space.x2))
            RText(label.uppercase(), Ryoku.t.label, Ryoku.c.inkMuted)
            Kv("Válidas", "$n")
            if (ev == null) StatusPill(Tone.Warn, "Faltan datos: ${n}/${Evaluation.MIN_TRAIN + 1}")
            else {
                Kv("Evaluadas", "${ev.n}")
                Kv("Error horario", "%.1f min (sesgo %+.1f)".format(ev.maeSchedule / 60, ev.biasSchedule / 60))
                Kv("Error ajuste", "%.1f min (sesgo %+.1f)".format(ev.maeModel / 60, ev.biasModel / 60))
                if (ev.enabled) StatusPill(Tone.Ok, "Ajuste activo") else StatusPill(Tone.Warn, if (ev.n < Evaluation.MIN_EVAL) "Aún pocas: ${ev.n}/${Evaluation.MIN_EVAL}" else "Aún no mejora al horario")
            }
        }
        Spacer(Modifier.height(Space.x3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("Aplicar ajuste", Ryoku.t.bodyBold, modifier = Modifier.weight(1f))
            GhostButton(if (vm.adjustEnabled) "Sí" else "No", { vm.updateAdjust(!vm.adjustEnabled) }, selected = vm.adjustEnabled)
        }
        Spacer(Modifier.height(Space.x2))
        if (!confirmClear) GhostButton("Borrar anotaciones…", { confirmClear = true }, Modifier.fillMaxWidth())
        else Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            GhostButton("Cancelar", { confirmClear = false }, Modifier.weight(1f))
            GhostButton("Borrar todo", { vm.clearObservations(); confirmClear = false }, Modifier.weight(1f))
        }
    }
}

@Composable private fun Kv(k: String, v: String) = Row(Modifier.padding(vertical = 2.dp)) {
    RText(k, Ryoku.t.small, Ryoku.c.inkMuted, Modifier.width(96.dp)); RText(v, Ryoku.t.small, Ryoku.c.ink, Modifier.weight(1f))
}

@Composable private fun Cap(name: String, tone: Tone, note: String) = Row(Modifier.fillMaxWidth().padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { RText(name, Ryoku.t.body); RText(note, Ryoku.t.small, Ryoku.c.inkMuted) }
    StatusPill(tone, when (tone) { Tone.Ok -> "sí"; Tone.Error -> "no"; Tone.Warn -> "parcial"; Tone.Info -> "parcial" })
}
