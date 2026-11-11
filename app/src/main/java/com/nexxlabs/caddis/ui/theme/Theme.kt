package com.nexxlabs.caddis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nexxlabs.caddis.R

/** The prototype's tokens: warm paper neutrals with one river-teal accent. */
@Immutable
data class CaddisColors(
    val bg: Color,
    val surface: Color,
    val tint: Color,
    val line: Color,
    val fg: Color,
    val fg2: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val good: Color,
    val warn: Color,
    val crit: Color,
    val scrim: Color,
    val toastLink: Color,
)

val LightColors = CaddisColors(
    bg = Color(0xFFFAF8F4), surface = Color(0xFFFFFFFF), tint = Color(0xFFF0ECE4), line = Color(0xFFDDD6CA),
    fg = Color(0xFF1F2421), fg2 = Color(0xFF5C645E), accent = Color(0xFF1F6F68), accentTint = Color(0xFFDCEBE8),
    onAccent = Color(0xFFFFFFFF), good = Color(0xFF2E7D4F), warn = Color(0xFF9A6412), crit = Color(0xFFB3372E),
    scrim = Color(0x6B141815), toastLink = Color(0xFF9BE3D8),
)

val DarkColors = CaddisColors(
    bg = Color(0xFF121614), surface = Color(0xFF1B201D), tint = Color(0xFF242B27), line = Color(0xFF343D38),
    fg = Color(0xFFECEFEA), fg2 = Color(0xFFA5AEA8), accent = Color(0xFF5CC2B5), accentTint = Color(0xFF1D3A36),
    onAccent = Color(0xFF0E1F1C), good = Color(0xFF5DBB86), warn = Color(0xFFE0B04F), crit = Color(0xFFF0877D),
    scrim = Color(0x99000000), toastLink = Color(0xFF1F6F68),
)

val LocalCaddisColors = staticCompositionLocalOf { LightColors }

/** Bricolage Grotesque for titles, DM Sans for UI (variable fonts bundled in res/font). */
@OptIn(ExperimentalTextApi::class)
private fun display(weight: Int) = Font(
    R.font.bricolage_grotesque, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 36f)),
)

@OptIn(ExperimentalTextApi::class)
private fun body(weight: Int) = Font(
    R.font.dm_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 14f)),
)

val Display = FontFamily(display(500), display(600), display(700))
val Body = FontFamily(body(400), body(500), body(600), body(700))

object CaddisType {
    val h1 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-0.02).em)
    val h2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em)
    val section = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-0.02).em)
    val sheetTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
    val itemTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em)
    val stat = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val strong = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val cardTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp)
    val sub = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val fine = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val eyebrow = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.06.em)
    val button = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val buttonSm = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val tag = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
}

object C {
    val colors: CaddisColors
        @Composable get() = LocalCaddisColors.current
}

@Composable
fun CaddisTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onSurface = c.fg, onBackground = c.fg, outline = c.line, error = c.crit, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onSurface = c.fg, onBackground = c.fg, outline = c.line, error = c.crit, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface)
    }
    CompositionLocalProvider(LocalCaddisColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = CaddisType.bodyLarge, bodyMedium = CaddisType.body), content = content)
    }
}
