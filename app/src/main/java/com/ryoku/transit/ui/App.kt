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
