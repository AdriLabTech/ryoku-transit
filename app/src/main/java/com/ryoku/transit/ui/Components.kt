package com.ryoku.transit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryoku.transit.domain.Arrival
import com.ryoku.transit.domain.Basis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.BasicText

private val Shape = RoundedCornerShape(Size.radius)
val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun RText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color = Ryoku.c.ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Marcador de sección: `00 // PARADAS`. */
@Composable
fun SectionLabel(index: Int, text: String, modifier: Modifier = Modifier) =
    RText("%02d // %s".format(index, text.uppercase()), Ryoku.t.label, Ryoku.c.inkMuted, modifier.semantics { heading() })

/** Color de texto con contraste suficiente sobre un color de línea del feed. */
fun readableOn(bg: Long): Color {
    fun lin(v: Int): Double { val c = v / 255.0; return if (c <= .03928) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4) }
    val l = .2126 * lin(((bg shr 16) and 0xFF).toInt()) + .7152 * lin(((bg shr 8) and 0xFF).toInt()) + .0722 * lin((bg and 0xFF).toInt())
    return if ((l + .05) / .05 >= 1.05 / (l + .05)) Color.Black else Color.White
}

@Composable
fun RouteBadge(shortName: String, color: Long, modifier: Modifier = Modifier) {
    val bg = Color(0xFF000000 or color)
    Box(
        modifier.defaultMinSize(minWidth = 44.dp, minHeight = 32.dp).clip(Shape).background(bg).padding(horizontal = Space.x2),
        contentAlignment = Alignment.Center,
    ) { RText(shortName, Ryoku.t.h3, readableOn(color)) }
}

/** Disco bermellón: marca de Ryoku, sin símbolos. */
@Composable
fun BrandDisc(size: Int = 14) = Box(Modifier.size(size.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Ryoku.c.brand))

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(Shape).background(Ryoku.c.raised).border(BorderStroke(1.dp, Ryoku.c.line1), Shape)
    Column((if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m).padding(Space.x4), content = content)
}

/** Estado nunca solo en color: icono de texto + palabra. */
enum class Tone { Ok, Warn, Error, Info }

@Composable
fun StatusPill(tone: Tone, text: String, modifier: Modifier = Modifier) {
    val c = Ryoku.c
    val (glyph, color) = when (tone) { Tone.Ok -> "✓" to c.ok; Tone.Warn -> "▲" to c.warn; Tone.Error -> "✕" to c.error; Tone.Info -> "●" to c.secondary }
    Row(
        modifier.clip(Shape).border(1.dp, color.copy(alpha = .6f), Shape).padding(horizontal = Space.x2, vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        RText(glyph, Ryoku.t.small, color)
        RText(text, Ryoku.t.small, color)
    }
}

/** Banner de aviso con barra izquierda tonal. */
@Composable
fun Banner(tone: Tone, title: String, body: String, modifier: Modifier = Modifier) {
    val c = Ryoku.c
    val color = when (tone) { Tone.Ok -> c.ok; Tone.Warn -> c.warn; Tone.Error -> c.error; Tone.Info -> c.secondary }
    val glyph = when (tone) { Tone.Ok -> "✓"; Tone.Warn -> "▲"; Tone.Error -> "✕"; Tone.Info -> "●" }
    Row(
        modifier.fillMaxWidth().clip(Shape).background(c.raised).border(1.dp, c.line1, Shape).height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            RText("$glyph $title", Ryoku.t.bodyBold, color)
            RText(body, Ryoku.t.small, c.inkDim)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, visibleHint: String = hint) {
    val c = Ryoku.c
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = Ryoku.t.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().heightIn(min = Size.control).clip(Shape).background(c.paper)
            .border(1.dp, c.line3, Shape).padding(horizontal = Space.x3).semantics { contentDescription = hint },
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = Size.control), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) RText(visibleHint, Ryoku.t.body, c.inkFaint, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
fun FavoriteToggle(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ryoku.c
    Box(
        modifier.size(Size.control).clip(Shape).border(1.dp, if (isFavorite) c.gold else c.line2, Shape)
            .clickable(role = Role.Switch, onClickLabel = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos", onClick = onToggle)
            .semantics { stateDescription = if (isFavorite) "Favorita" else "No favorita"; contentDescription = "Favorita" },
        contentAlignment = Alignment.Center,
    ) { RText(if (isFavorite) "★" else "☆", Ryoku.t.h2, if (isFavorite) c.gold else c.inkDim) }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        RText("+  $title", Ryoku.t.h3, Ryoku.c.inkDim)
        RText(body, Ryoku.t.small, Ryoku.c.inkMuted)
    }
}

@Composable
fun LoadingBlock(label: String = "Cargando", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(Space.x6).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = label }) {
        repeat(3) { Box(Modifier.padding(vertical = Space.x2).fillMaxWidth().height(56.dp).clip(Shape).background(Ryoku.c.high.copy(alpha = .5f))) }
    }
}

/**
 * Fila de llegada: minutos grandes y fijos a la izquierda, línea y destino, hora y base de la hora.
 * Al tocarla se despliega el panel para anotar cuándo llegó realmente (alimenta el aprendizaje).
 */
@Composable
fun ArrivalRow(a: Arrival, observed: Boolean, onRecord: (minutesAgo: Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ryoku.c
    var open by remember(a.tripId, a.serviceDate) { mutableStateOf(false) }
    val ref = a.basis == Basis.REFERENCE
    val eff = a.adjustedMinutes ?: a.minutes
    fun hm(sec: Int) = "%02d:%02d".format((sec / 3600) % 24, (sec / 60) % 60)
    val hhmm = hm(a.scheduledSec)
    val big = when { eff < 0 -> "¿pasó?"; eff == 0 -> "ya"; else -> "$eff" }
    val tag = when {
        a.adjustedSec != null -> "$hhmm → ≈${hm(a.adjustedSec)} · AJUSTADO (${a.adjustSupport} obs.)"
        a.minutes < 0 -> "$hhmm · programado hace ${-a.minutes} min" + if (ref) " · REF" else ""
        ref -> "$hhmm · REF ${a.serviceDate.format(DateTimeFormatter.ofPattern("dd/MM"))}"
        else -> "$hhmm · HORARIO"
    } + if (observed) " · ✓ anotado" else ""
    val tagColor = when { a.adjustedSec != null -> c.secondary; ref -> c.warn; else -> c.inkMuted }
    val desc = "Línea ${a.routeShortName} hacia ${a.headsign}, " +
        (when { eff < 0 -> "debería haber pasado ya"; eff == 0 -> "llega ahora"; else -> "en $eff minutos" }) +
        ", salida programada a las $hhmm, " +
        (if (a.adjustedSec != null) "corregida con ${a.adjustSupport} observaciones propias, " else "") +
        (if (ref) "horario de referencia, no es información de hoy" else "horario publicado, no es posición en vivo") +
        (if (observed) ", ya anotada" else ", toca para anotar cuándo llegó")
    Column(modifier.fillMaxWidth().clip(Shape).background(c.raised).border(1.dp, if (open) c.line3 else if (ref) c.line2 else c.line1, Shape)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = "Anotar llegada real") { open = !open }
                .padding(Space.x3).semantics(mergeDescendants = true) { contentDescription = desc },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Row(Modifier.width(84.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                RText(big, if (eff < 0) Ryoku.t.h3 else Ryoku.t.minutes, if (eff < 0) c.warn else if (ref) c.inkDim else c.ink)
                if (eff > 0) RText("min", Ryoku.t.small, c.inkMuted, Modifier.padding(bottom = 4.dp))
            }
            RouteBadge(a.routeShortName, a.routeColor)
            Column(Modifier.weight(1f)) {
                RText(a.headsign, Ryoku.t.bodyBold, c.ink, maxLines = 1)
                RText(tag, Ryoku.t.small, tagColor, maxLines = 2)
            }
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = Space.x3, end = Space.x3, bottom = Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            RText("¿CUÁNDO LLEGÓ DE VERDAD?", Ryoku.t.label, c.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                GhostButton("Ahora", { onRecord(0); open = false }, Modifier.weight(1f))
                GhostButton("−1 min", { onRecord(1); open = false }, Modifier.weight(1f))
                GhostButton("−2 min", { onRecord(2); open = false }, Modifier.weight(1f))
                GhostButton("−5 min", { onRecord(5); open = false }, Modifier.weight(1f))
            }
            RText("Se guarda solo en este móvil. Con suficientes anotaciones, la app corrige sus horas.", Ryoku.t.small, c.inkFaint)
        }
    }
}

@Composable
fun freshnessLabel(at: Instant, now: Instant): String {
    val mins = java.time.Duration.between(at, now).toMinutes()
    val hhmm = HHMM.format(at.atZone(ZoneId.of("Europe/Madrid")))
    return if (mins < 1) "Actualizado a las $hhmm" else "Actualizado a las $hhmm (hace $mins min)"
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val c = Ryoku.c
    Box(
        modifier.heightIn(min = Size.control).clip(Shape)
            .background(if (selected) c.ink else Color.Transparent).border(1.dp, if (selected) c.ink else c.line3, Shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.x4),
        contentAlignment = Alignment.Center,
    ) { RText(text, Ryoku.t.bodyBold, if (selected) c.onInk else c.ink) }
}
