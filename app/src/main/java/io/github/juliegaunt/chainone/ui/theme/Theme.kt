package io.github.juliegaunt.chainone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// A pink palette built around #D14D72 (the button color), using Material's tonal steps for the
// lighter and darker shades. Used on every phone so the app always looks the same.
private val LightColors = lightColorScheme(
    primary = Color(0xFFD14D72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = Color(0xFF3F0017),
    secondary = Color(0xFF75565C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DF),
    onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7A5732),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBC),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF3DDE0),
    onSurfaceVariant = Color(0xFF524345),
    outline = Color(0xFF847375),
    outlineVariant = Color(0xFFD6C2C4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF1F1),
    surfaceContainer = Color(0xFFF8EBEC),
    surfaceContainerHigh = Color(0xFFF2E5E6),
    surfaceContainerHighest = Color(0xFFECE0E0),
    inverseSurface = Color(0xFF352F30),
    inverseOnSurface = Color(0xFFFAEEEF),
    inversePrimary = Color(0xFFFFB1C0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C0),
    onPrimary = Color(0xFF660029),
    primaryContainer = Color(0xFF8A143E),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFE4BDC3),
    onSecondary = Color(0xFF43292E),
    secondaryContainer = Color(0xFF5B3F44),
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = Color(0xFFECBE91),
    onTertiary = Color(0xFF462A09),
    tertiaryContainer = Color(0xFF60401D),
    onTertiaryContainer = Color(0xFFFFDCBC),
    background = Color(0xFF171213),
    onBackground = Color(0xFFECE0E0),
    surface = Color(0xFF171213),
    onSurface = Color(0xFFECE0E0),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD6C2C4),
    outline = Color(0xFF9F8C8F),
    outlineVariant = Color(0xFF524345),
    surfaceContainerLowest = Color(0xFF120D0E),
    surfaceContainerLow = Color(0xFF201A1B),
    surfaceContainer = Color(0xFF241E1F),
    surfaceContainerHigh = Color(0xFF2F2829),
    surfaceContainerHighest = Color(0xFF3A3334),
    inverseSurface = Color(0xFFECE0E0),
    inverseOnSurface = Color(0xFF352F30),
    inversePrimary = Color(0xFFAA2F55),
)

/** Colors for one pattern status: [container] fills a selected chip, [accent] outlines an unselected one. */
data class StatusColor(val container: Color, val content: Color, val accent: Color)

data class StatusColors(val wantToMake: StatusColor, val inProgress: StatusColor, val finished: StatusColor)

// Want to make is pink (the app's main color), In progress amber, Finished green.
private val LightStatusColors = StatusColors(
    wantToMake = StatusColor(container = Color(0xFFFFD9DF), content = Color(0xFF3F0017), accent = Color(0xFFD14D72)),
    inProgress = StatusColor(container = Color(0xFFFFDDB3), content = Color(0xFF291800), accent = Color(0xFF8B5000)),
    finished = StatusColor(container = Color(0xFFC3EFC6), content = Color(0xFF002109), accent = Color(0xFF2E6B30)),
)

private val DarkStatusColors = StatusColors(
    wantToMake = StatusColor(container = Color(0xFF8A143E), content = Color(0xFFFFD9DF), accent = Color(0xFFFFB1C0)),
    inProgress = StatusColor(container = Color(0xFF693C00), content = Color(0xFFFFDDB3), accent = Color(0xFFFFB95C)),
    finished = StatusColor(container = Color(0xFF145221), content = Color(0xFFC3EFC6), accent = Color(0xFF8FD694)),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun ChainOneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
