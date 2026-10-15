package com.ryoku.transit.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryoku.transit.R

/** Tokens semánticos Ryoku Kami. Valores de `Estilo visual Ryoku Kami` (vault). Nada de colores sueltos en pantallas. */
@Immutable
class RyokuColors(
    val paper: Color, val raised: Color, val high: Color,
    val ink: Color, val inkDim: Color, val inkMuted: Color, val inkFaint: Color,
    val secondary: Color, val gold: Color, val brand: Color, val brandText: Color,
    val error: Color, val warn: Color, val ok: Color,
    val line1: Color, val line2: Color, val line3: Color,
    val onInk: Color,
    val isDark: Boolean,
)

val Grafito = RyokuColors(
    paper = Color(0xFF211D1A), raised = Color(0xFF2A2521), high = Color(0xFF352F2A),
    ink = Color(0xFFECE4D8), inkDim = Color(0xFFC9C0B3), inkMuted = Color(0xFFA69D90), inkFaint = Color(0xFF8F867A),
    secondary = Color(0xFF8FBDD1), gold = Color(0xFFD9A441), brand = Color(0xFFE2342A), brandText = Color(0xFFF4665A),
    error = Color(0xFFF27A70), warn = Color(0xFFF0B25E), ok = Color(0xFF7FD0AE),
    line1 = Color(0xFFECE4D8).copy(alpha = .16f), line2 = Color(0xFFECE4D8).copy(alpha = .30f), line3 = Color(0xFFECE4D8).copy(alpha = .48f),
    onInk = Color(0xFF211D1A), isDark = true,
)

val Washi = RyokuColors(
    paper = Color(0xFFF3ECE0), raised = Color(0xFFFAF5EC), high = Color(0xFFE9E0D1),
    ink = Color(0xFF26211C), inkDim = Color(0xFF4A423A), inkMuted = Color(0xFF675D52), inkFaint = Color(0xFF8A8074),
    secondary = Color(0xFF2B6A86), gold = Color(0xFF9A6B00), brand = Color(0xFFE2342A), brandText = Color(0xFFC42A21),
    error = Color(0xFFB3261E), warn = Color(0xFF8A5A00), ok = Color(0xFF1F6F4D),
    line1 = Color(0xFF26211C).copy(alpha = .16f), line2 = Color(0xFF26211C).copy(alpha = .30f), line3 = Color(0xFF26211C).copy(alpha = .48f),
    onInk = Color(0xFFF3ECE0), isDark = false,
)

val Komiki = FontFamily(
    Font(R.font.komiki_mono_regular, FontWeight.Normal),
    Font(R.font.komiki_mono_bold, FontWeight.Bold),
)

/** Escala del sistema: Display 40/48 · H1 28/36 · H2 20/28 · H3 16/24 · Cuerpo 14/22 · Pequeño 12/18 · Etiqueta 11/16. */
@Immutable
class RyokuType(
    val display: TextStyle = style(40, 48, bold = true),
    val h1: TextStyle = style(28, 36, bold = true),
    val h2: TextStyle = style(20, 28, bold = true),
    val h3: TextStyle = style(16, 24, bold = true),
    val body: TextStyle = style(14, 22),
    val bodyBold: TextStyle = style(14, 22, bold = true),
    val small: TextStyle = style(12, 18),
    val label: TextStyle = style(11, 16, bold = true, tracking = .08f),
) {
    /** Minutos de llegada: tamaño de H1 con cifras de ancho fijo (Komiki Mono es monoespaciada). */
    val minutes: TextStyle get() = h1
}

private fun style(size: Int, line: Int, bold: Boolean = false, tracking: Float = 0f) = TextStyle(
    fontFamily = Komiki, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, letterSpacing = (tracking * size).sp,
)

object Space { val x1 = 4.dp; val x2 = 8.dp; val x3 = 12.dp; val x4 = 16.dp; val x6 = 24.dp; val x8 = 32.dp; val x12 = 48.dp }
object Size { val control = 48.dp /* mínimo táctil; el sistema pide 40 y Android 48 */ ; val radius = 4.dp }

val LocalRyoku = staticCompositionLocalOf { Grafito }
val LocalRyokuType = staticCompositionLocalOf { RyokuType() }

object Ryoku {
    val c: RyokuColors @Composable get() = LocalRyoku.current
    val t: RyokuType @Composable get() = LocalRyokuType.current
}

enum class ThemeMode { GRAFITO, WASHI }

@Composable
fun RyokuTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (mode == ThemeMode.GRAFITO) Grafito else Washi
    CompositionLocalProvider(LocalRyoku provides colors, LocalRyokuType provides remember { RyokuType() }, content = content)
}
