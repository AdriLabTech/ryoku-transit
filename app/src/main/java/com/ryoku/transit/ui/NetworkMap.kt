package com.ryoku.transit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ryoku.transit.data.LatLon
import com.ryoku.transit.domain.Stop
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Mapa esquemático "plano a tinta": geometrías y paradas del GTFS sobre una proyección equirrectangular,
 * sin teselas ni servicios de terceros. Gestos: arrastrar, pellizcar, tocar una parada.
 */
@Composable
fun NetworkMap(
    shapes: List<List<LatLon>>,
    stops: List<Stop>,
    modifier: Modifier = Modifier,
    highlight: List<LatLon> = emptyList(),
    highlightStops: List<Stop> = emptyList(),
    selected: Stop? = null,
    fit: List<LatLon> = emptyList(),
    onStopTap: (Stop) -> Unit = {},
    description: String,
) {
    val c = Ryoku.c
    val all = remember(shapes, stops) { stops.map { LatLon(it.lat, it.lon) } }
    val proj = remember(all) { Projection(all) }
    val networkPath = remember(shapes, proj) { buildPath(shapes, proj) }
    val hiPath = remember(highlight, proj) { buildPath(listOf(highlight), proj) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember(fit) { mutableStateOf(false) }
    val density = LocalDensity.current.density

    Canvas(
        modifier.fillMaxSize().clipToBounds()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(minScale(size.width.toFloat(), size.height.toFloat(), proj), 60f * minScale(size.width.toFloat(), size.height.toFloat(), proj))
                    val f = ns / scale
                    offset = (offset - centroid) * f + centroid + pan
                    scale = ns
                }
            }
            .pointerInput(stops, proj) {
                detectTapGestures { tap ->
                    val world = Offset((tap.x - offset.x) / scale, (tap.y - offset.y) / scale)
                    val reach = 28f * density / scale
                    val hit = stops.minByOrNull { val p = proj.xy(it.lat, it.lon); hypot(p.x - world.x, p.y - world.y) }
                    if (hit != null) {
                        val p = proj.xy(hit.lat, hit.lon)
                        if (hypot(p.x - world.x, p.y - world.y) <= reach) onStopTap(hit)
                    }
                }
            },
    ) {
        if (!fitted) {
            val pts = (if (fit.isEmpty()) all else fit).map { proj.xy(it.lat, it.lon) }
            val x0 = pts.minOf { it.x }; val x1 = pts.maxOf { it.x }; val y0 = pts.minOf { it.y }; val y1 = pts.maxOf { it.y }
            val bw = max(x1 - x0, 1f); val bh = max(y1 - y0, 1f)
            val s = min(size.width / bw, size.height / bh) * .9f
            scale = s; offset = Offset((size.width - bw * s) / 2f - x0 * s, (size.height - bh * s) / 2f - y0 * s); fitted = true
        }
        // Fondo de puntos de 16 dp (motivo Ryoku Kami).
        val step = 16f * density
        var y = 0f
        while (y < size.height) { var x = 0f; while (x < size.width) { drawCircle(c.ink.copy(alpha = .10f), 1.2f * density, Offset(x, y)); x += step }; y += step }

        withTransform({ translate(offset.x, offset.y); scale(scale, scale, Offset.Zero) }) {
            val w = 1.5f * density / scale
            drawPath(networkPath, c.inkMuted.copy(alpha = if (highlight.isEmpty()) .55f else .22f), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (highlight.isNotEmpty()) drawPath(hiPath, c.secondary, style = Stroke(3.5f * density / scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val r = 1.5f * density / scale
            for (s in stops) {
                val p = proj.xy(s.lat, s.lon)
                drawRect(c.ink.copy(alpha = if (highlight.isEmpty()) .85f else .3f), Offset(p.x - r, p.y - r), Size(2 * r, 2 * r))
            }
            for (s in highlightStops) {
                val p = proj.xy(s.lat, s.lon); val rr = 3f * density / scale
                drawRect(c.paper, Offset(p.x - rr, p.y - rr), Size(2 * rr, 2 * rr))
                drawRect(c.secondary, Offset(p.x - rr, p.y - rr), Size(2 * rr, 2 * rr), style = Stroke(2f * density / scale))
            }
            selected?.let {
                val p = proj.xy(it.lat, it.lon); val rr = 5f * density / scale
                drawCircle(c.brand, rr, Offset(p.x, p.y))
                drawCircle(c.paper, rr, Offset(p.x, p.y), style = Stroke(1.5f * density / scale))
            }
        }
    }
}

private fun minScale(w: Float, h: Float, p: Projection) = min(w / p.w, h / p.h) * .5f

private fun buildPath(lines: List<List<LatLon>>, p: Projection) = Path().apply {
    for (l in lines) l.forEachIndexed { i, pt -> val o = p.xy(pt.lat, pt.lon); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
}

/** Proyección equirrectangular local: x = lon·cos(lat0), y = −lat; escala arbitraria (1 unidad ≈ 1 grado × 1000). */
class Projection(points: List<LatLon>) {
    private val minLat = points.minOf { it.lat }; private val maxLat = points.maxOf { it.lat }
    private val minLon = points.minOf { it.lon }; private val k = cos(Math.toRadians((minLat + maxLat) / 2))
    private val maxLon = points.maxOf { it.lon }
    val w = max(((maxLon - minLon) * k * 1000).toFloat(), 1f)
    val h = max(((maxLat - minLat) * 1000).toFloat(), 1f)
    fun xy(lat: Double, lon: Double) = Offset(((lon - minLon) * k * 1000).toFloat(), ((maxLat - lat) * 1000).toFloat())
}
