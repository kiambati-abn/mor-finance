package com.morphlabs.morph.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Brand colours sampled from the Morph Labs logo. */
object Brand {
    val Purple = Color(0xFF382078)
    val PurpleDeep = Color(0xFF2E1260)
    val Gold = Color(0xFFF0C810)
    val Amber = Color(0xFFE8A020)
    val Teal = Color(0xFF007088)
    val Good = Color(0xFF2E9D5B)
    val Bad = Color(0xFFD64545)
}

@Immutable
data class MorphExtras(val hero: Brush, val onHero: Color, val gold: Color, val good: Color, val bad: Color, val track: Color)

val LocalMorph = staticCompositionLocalOf {
    MorphExtras(Brush.linearGradient(listOf(Brand.Purple, Brand.Teal)), Color.White, Brand.Amber, Brand.Good, Brand.Bad, Color.LightGray)
}

private val Light = lightColorScheme(
    primary = Brand.Purple, onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF7A5200), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A), onSecondaryContainer = Color(0xFF261A00),
    tertiary = Brand.Teal, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8EAFF), onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFFBF9FE), onBackground = Color(0xFF1C1A22),
    surface = Color(0xFFFBF9FE), onSurface = Color(0xFF1C1A22),
    surfaceVariant = Color(0xFFE8E0EF), onSurfaceVariant = Color(0xFF4A4458),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2FB),
    surfaceContainer = Color(0xFFF1ECF7), surfaceContainerHigh = Color(0xFFEBE5F3),
    surfaceContainerHighest = Color(0xFFE5DFEE),
    outline = Color(0xFF7B7389), outlineVariant = Color(0xFFCCC4D6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFCFBDFF), onPrimary = Color(0xFF2A1360),
    primaryContainer = Brand.Purple, onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Brand.Gold, onSecondary = Color(0xFF3B2B00),
    secondaryContainer = Color(0xFF5A4300), onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFF5DD5F5), onTertiary = Color(0xFF003544),
    tertiaryContainer = Color(0xFF004E60), onTertiaryContainer = Color(0xFFB8EAFF),
    background = Color(0xFF120B1F), onBackground = Color(0xFFE8E0F0),
    surface = Color(0xFF120B1F), onSurface = Color(0xFFE8E0F0),
    surfaceVariant = Color(0xFF2E2640), onSurfaceVariant = Color(0xFFCBC2DB),
    surfaceContainerLowest = Color(0xFF0D0717), surfaceContainerLow = Color(0xFF1A1229),
    surfaceContainer = Color(0xFF1F162F), surfaceContainerHigh = Color(0xFF291F3A),
    surfaceContainerHighest = Color(0xFF342945),
    outline = Color(0xFF958CA5), outlineVariant = Color(0xFF4A4259),
)

@Composable
fun MorphTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val extras = if (dark) MorphExtras(
        hero = Brush.linearGradient(listOf(Color(0xFF4A2A99), Color(0xFF1E3F6E), Brand.Teal)),
        onHero = Color.White, gold = Brand.Gold, good = Color(0xFF5BD08A), bad = Color(0xFFFF7A7A),
        track = Color(0xFF342945),
    ) else MorphExtras(
        hero = Brush.linearGradient(listOf(Brand.Purple, Color(0xFF3B3C8F), Brand.Teal)),
        onHero = Color.White, gold = Brand.Amber, good = Brand.Good, bad = Brand.Bad,
        track = Color(0xFFE5DFEE),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalMorph provides extras) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
