package com.ryoku.transit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.ryoku.transit.AppViewModel

private data class Tab(val route: String, val label: String)
private val TABS = listOf(Tab("stops", "Paradas"), Tab("lines", "Líneas"), Tab("map", "Mapa"), Tab("favs", "Favoritas"), Tab("notices", "Avisos"))

@Composable
fun RyokuApp(vm: AppViewModel) {
    RyokuTheme(vm.theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination?.route
        fun tab(r: String) = nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
        val status = { nav.navigate("status") }
        Column(Modifier.fillMaxSize().background(Ryoku.c.paper).statusBarsPadding()) {
            vm.pending?.let { BoardingConfirm(vm, it) }
            NavHost(nav, "stops", Modifier.weight(1f)) {
                composable("stops") { StopsScreen(vm, { nav.navigate("stop/$it") }, status) }
                composable("lines") { LinesScreen(vm, { nav.navigate("line/$it") }, status) }
                composable("map") { MapScreen(vm, { nav.navigate("stop/$it") }, { tab("stops") }, status) }
                composable("favs") { FavoritesScreen(vm, { nav.navigate("stop/$it") }, status) }
                composable("notices") { NoticesScreen(vm, status) }
                composable("stop/{id}") { StopDetailScreen(vm, it.arguments!!.getString("id")!!, { nav.popBackStack() }, { l -> nav.navigate("line/$l") }) }
                composable("line/{id}") { LineDetailScreen(vm, it.arguments!!.getString("id")!!, { nav.popBackStack() }, { s -> nav.navigate("stop/$s") }) }
                composable("status") { StatusScreen(vm) { nav.popBackStack() } }
            }
            // La barra inferior limita la escala de texto (1,2×): cinco etiquetas no caben en una línea con más.
            val d = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, minOf(d.fontScale, 1.2f))) {
            Row(
                Modifier.fillMaxWidth().background(Ryoku.c.raised).navigationBarsPadding().semantics { collectionInfo = CollectionInfo(1, TABS.size) },
            ) {
                TABS.forEach { t ->
                    val on = current == t.route
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp)
                            .clickable(role = Role.Tab) { tab(t.route) }
                            .semantics { selected = on; contentDescription = t.label },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.width(24.dp).height(2.dp).background(if (on) Ryoku.c.ink else androidx.compose.ui.graphics.Color.Transparent))
                        Spacer(Modifier.height(Space.x2))
                        RText(t.label.uppercase(), Ryoku.t.label, if (on) Ryoku.c.ink else Ryoku.c.inkMuted, maxLines = 1)
                    }
                }
            }
            }
        }
    }
}

/** Tarjeta para confirmar qué línea cogió el usuario tras una subida detectada. */
@Composable
private fun BoardingConfirm(vm: AppViewModel, ev: com.ryoku.transit.domain.BoardingEvent) {
    val lines = remember(ev, vm.calculator) { vm.boardingCandidates(ev).distinctBy { it.routeId } }
    val stop = vm.schedule?.stop(ev.stopId)
    val at = java.time.Instant.ofEpochMilli(ev.movedAtMs).atZone(vm.zone).format(HHMM)
    Column(Modifier.fillMaxWidth().padding(Space.x4).background(Ryoku.c.raised).padding(Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        RText("● ¿SUBISTE A UN AUTOBÚS?", Ryoku.t.label, Ryoku.c.secondary)
        RText("Detecté que saliste de ${stop?.name ?: "la parada"} a las $at a velocidad de vehículo. ¿Qué línea cogiste?", Ryoku.t.body)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            lines.take(5).forEach { a -> RouteBadge(a.routeShortName, a.routeColor, Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Confirmar línea ${a.routeShortName}") { vm.confirmBoarding(ev, a.routeId) }) }
            if (lines.isEmpty()) RText("Sin líneas programadas en ese momento.", Ryoku.t.small, Ryoku.c.inkMuted)
        }
        GhostButton("No era un autobús", { vm.dismissPending() })
    }
}
