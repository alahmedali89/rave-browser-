package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class SeedPalettePreset(val label: String, val seedColor: Color) {
    ELECTRIC_COBALT("Electric Cobalt", Color(0xFF0066FF)),
    CYBER_EMERALD("Cyber Emerald", Color(0xFF00A878)),
    AMETHYST_PULSE("Amethyst Pulse", Color(0xFF7B42F6)),
    SOLAR_CORAL("Solar Coral", Color(0xFFE85D3F))
}

/**
 * Tonal color options for Tab Groups and individual Tabs, derived dynamically
 * for light and dark Material 3 themes without hardcoded hex colors in UI composables.
 */
enum class TabGroupColorOption(
    val id: String,
    val label: String,
    val hueDegrees: Float
) {
    COBALT("cobalt", "Cobalt", 218f),
    EMERALD("emerald", "Emerald", 155f),
    VIOLET("violet", "Violet", 272f),
    CORAL("coral", "Coral", 14f),
    AMBER("amber", "Amber", 42f),
    CYAN("cyan", "Cyan", 192f),
    ROSE("rose", "Rose", 336f);

    fun next(): TabGroupColorOption {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

@Immutable
data class TabGroupTonalColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val subtleSurface: Color,
    val border: Color
)

@Composable
fun TabGroupColorOption.resolveTonalColors(
    darkTheme: Boolean = LocalApexDarkTheme.current ?: isSystemInDarkTheme()
): TabGroupTonalColors {
    return ApexColorSchemeBuilder.groupTonalColors(this, darkTheme)
}

/**
 * Generates a full Material 3 Expressive tonal [ColorScheme] from any [seed] color,
 * mirroring `ColorScheme.fromSeed()` with full tonal surface container roles.
 */
object ApexColorSchemeBuilder {

    fun groupTonalColors(option: TabGroupColorOption, darkTheme: Boolean): TabGroupTonalColors {
        val h = option.hueDegrees
        return if (darkTheme) {
            TabGroupTonalColors(
                accent = hslToComposeColor(h, 0.85f, 0.72f),
                onAccent = hslToComposeColor(h, 0.88f, 0.14f),
                container = hslToComposeColor(h, 0.65f, 0.26f),
                onContainer = hslToComposeColor(h, 0.90f, 0.92f),
                subtleSurface = hslToComposeColor(h, 0.42f, 0.20f),
                border = hslToComposeColor(h, 0.76f, 0.62f)
            )
        } else {
            var accentLightness = 0.38f
            var accent = hslToComposeColor(h, 0.84f, accentLightness)
            while (accent.luminance() > 0.14f && accentLightness > 0.20f) {
                accentLightness -= 0.02f
                accent = hslToComposeColor(h, 0.84f, accentLightness)
            }
            TabGroupTonalColors(
                accent = accent,
                onAccent = hslToComposeColor(h, 0.22f, 0.96f),
                container = hslToComposeColor(h, 0.80f, 0.86f),
                onContainer = hslToComposeColor(h, 0.85f, 0.13f),
                subtleSurface = hslToComposeColor(h, 0.58f, 0.91f),
                border = hslToComposeColor(h, 0.72f, 0.44f)
            )
        }
    }

    /**
     * Harmonizes any [ColorScheme] (such as a system `dynamicLightColorScheme` / `dynamicDarkColorScheme`)
     * so its background, surface, surface containers, outlines, and scrim are always clearly theme-tinted
     * and never fall back to stark white (`#FFFFFF`) or stark black (`#000000`).
     */
    fun harmonizeSurfaceScheme(baseScheme: ColorScheme, darkTheme: Boolean): ColorScheme {
        val primaryHue = extractHueDegrees(baseScheme.primary)
        val secondaryHue = (primaryHue + 18f) % 360f
        val surfacePalette = buildSurfaceTokens(primaryHue = primaryHue, darkTheme = darkTheme)
        return baseScheme.copy(
            secondaryContainer = if (darkTheme) {
                tone(secondaryHue, 0.48f, 0.29f)
            } else {
                tone(secondaryHue, 0.54f, 0.76f)
            },
            onSecondaryContainer = if (darkTheme) {
                tone(secondaryHue, 0.45f, 0.95f)
            } else {
                tone(secondaryHue, 0.72f, 0.11f)
            },
            background = surfacePalette.background,
            onBackground = surfacePalette.onBackground,
            surface = surfacePalette.surface,
            onSurface = surfacePalette.onSurface,
            surfaceVariant = surfacePalette.surfaceVariant,
            onSurfaceVariant = surfacePalette.onSurfaceVariant,
            surfaceTint = baseScheme.primary,
            inverseSurface = surfacePalette.inverseSurface,
            inverseOnSurface = surfacePalette.inverseOnSurface,
            outline = surfacePalette.outline,
            outlineVariant = surfacePalette.outlineVariant,
            scrim = surfacePalette.scrim,
            surfaceBright = surfacePalette.surfaceBright,
            surfaceDim = surfacePalette.surfaceDim,
            surfaceContainerLowest = surfacePalette.surfaceContainerLowest,
            surfaceContainerLow = surfacePalette.surfaceContainerLow,
            surfaceContainer = surfacePalette.surfaceContainer,
            surfaceContainerHigh = surfacePalette.surfaceContainerHigh,
            surfaceContainerHighest = surfacePalette.surfaceContainerHighest
        )
    }

    fun fromSeed(seed: Color, darkTheme: Boolean): ColorScheme {
        val primaryHue = extractHueDegrees(seed)
        val secondaryHue = (primaryHue + 18f) % 360f
        val tertiaryHue = (primaryHue + 62f) % 360f
        val errorHue = 6f
        val surfacePalette = buildSurfaceTokens(primaryHue = primaryHue, darkTheme = darkTheme)

        return if (darkTheme) {
            darkColorScheme(
                primary = tone(primaryHue, 0.88f, 0.76f),
                onPrimary = tone(primaryHue, 0.90f, 0.14f),
                primaryContainer = tone(primaryHue, 0.68f, 0.27f),
                onPrimaryContainer = tone(primaryHue, 0.85f, 0.94f),
                inversePrimary = tone(primaryHue, 0.82f, 0.40f),
                secondary = tone(secondaryHue, 0.44f, 0.76f),
                onSecondary = tone(secondaryHue, 0.48f, 0.16f),
                secondaryContainer = tone(secondaryHue, 0.48f, 0.29f),
                onSecondaryContainer = tone(secondaryHue, 0.45f, 0.95f),
                tertiary = tone(tertiaryHue, 0.68f, 0.78f),
                onTertiary = tone(tertiaryHue, 0.72f, 0.15f),
                tertiaryContainer = tone(tertiaryHue, 0.56f, 0.27f),
                onTertiaryContainer = tone(tertiaryHue, 0.74f, 0.94f),
                background = surfacePalette.background,
                onBackground = surfacePalette.onBackground,
                surface = surfacePalette.surface,
                onSurface = surfacePalette.onSurface,
                surfaceVariant = surfacePalette.surfaceVariant,
                onSurfaceVariant = surfacePalette.onSurfaceVariant,
                surfaceTint = tone(primaryHue, 0.88f, 0.76f),
                inverseSurface = surfacePalette.inverseSurface,
                inverseOnSurface = surfacePalette.inverseOnSurface,
                error = tone(errorHue, 0.82f, 0.76f),
                onError = tone(errorHue, 0.85f, 0.16f),
                errorContainer = tone(errorHue, 0.70f, 0.28f),
                onErrorContainer = tone(errorHue, 0.88f, 0.91f),
                outline = surfacePalette.outline,
                outlineVariant = surfacePalette.outlineVariant,
                scrim = surfacePalette.scrim,
                surfaceBright = surfacePalette.surfaceBright,
                surfaceDim = surfacePalette.surfaceDim,
                surfaceContainerLowest = surfacePalette.surfaceContainerLowest,
                surfaceContainerLow = surfacePalette.surfaceContainerLow,
                surfaceContainer = surfacePalette.surfaceContainer,
                surfaceContainerHigh = surfacePalette.surfaceContainerHigh,
                surfaceContainerHighest = surfacePalette.surfaceContainerHighest
            )
        } else {
            lightColorScheme(
                primary = tone(primaryHue, 0.86f, 0.38f),
                onPrimary = tone(primaryHue, 0.24f, 0.97f),
                primaryContainer = tone(primaryHue, 0.84f, 0.86f),
                onPrimaryContainer = tone(primaryHue, 0.88f, 0.12f),
                inversePrimary = tone(primaryHue, 0.85f, 0.78f),
                secondary = tone(secondaryHue, 0.40f, 0.36f),
                onSecondary = tone(secondaryHue, 0.24f, 0.97f),
                secondaryContainer = tone(secondaryHue, 0.54f, 0.76f),
                onSecondaryContainer = tone(secondaryHue, 0.72f, 0.11f),
                tertiary = tone(tertiaryHue, 0.64f, 0.35f),
                onTertiary = tone(tertiaryHue, 0.24f, 0.97f),
                tertiaryContainer = tone(tertiaryHue, 0.72f, 0.85f),
                onTertiaryContainer = tone(tertiaryHue, 0.72f, 0.12f),
                background = surfacePalette.background,
                onBackground = surfacePalette.onBackground,
                surface = surfacePalette.surface,
                onSurface = surfacePalette.onSurface,
                surfaceVariant = surfacePalette.surfaceVariant,
                onSurfaceVariant = surfacePalette.onSurfaceVariant,
                surfaceTint = tone(primaryHue, 0.86f, 0.38f),
                inverseSurface = surfacePalette.inverseSurface,
                inverseOnSurface = surfacePalette.inverseOnSurface,
                error = tone(errorHue, 0.78f, 0.40f),
                onError = tone(errorHue, 0.24f, 0.97f),
                errorContainer = tone(errorHue, 0.80f, 0.87f),
                onErrorContainer = tone(errorHue, 0.82f, 0.13f),
                outline = surfacePalette.outline,
                outlineVariant = surfacePalette.outlineVariant,
                scrim = surfacePalette.scrim,
                surfaceBright = surfacePalette.surfaceBright,
                surfaceDim = surfacePalette.surfaceDim,
                surfaceContainerLowest = surfacePalette.surfaceContainerLowest,
                surfaceContainerLow = surfacePalette.surfaceContainerLow,
                surfaceContainer = surfacePalette.surfaceContainer,
                surfaceContainerHigh = surfacePalette.surfaceContainerHigh,
                surfaceContainerHighest = surfacePalette.surfaceContainerHighest
            )
        }
    }

    private data class HarmonizedSurfaceTokens(
        val background: Color,
        val onBackground: Color,
        val surface: Color,
        val onSurface: Color,
        val surfaceVariant: Color,
        val onSurfaceVariant: Color,
        val inverseSurface: Color,
        val inverseOnSurface: Color,
        val outline: Color,
        val outlineVariant: Color,
        val scrim: Color,
        val surfaceBright: Color,
        val surfaceDim: Color,
        val surfaceContainerLowest: Color,
        val surfaceContainerLow: Color,
        val surfaceContainer: Color,
        val surfaceContainerHigh: Color,
        val surfaceContainerHighest: Color
    )

    private fun buildSurfaceTokens(primaryHue: Float, darkTheme: Boolean): HarmonizedSurfaceTokens {
        return if (darkTheme) {
            HarmonizedSurfaceTokens(
                background = tone(primaryHue, 0.30f, 0.185f),
                onBackground = tone(primaryHue, 0.18f, 0.94f),
                surface = tone(primaryHue, 0.30f, 0.185f),
                onSurface = tone(primaryHue, 0.18f, 0.94f),
                surfaceVariant = tone(primaryHue, 0.28f, 0.27f),
                onSurfaceVariant = tone(primaryHue, 0.22f, 0.82f),
                inverseSurface = tone(primaryHue, 0.26f, 0.88f),
                inverseOnSurface = tone(primaryHue, 0.28f, 0.17f),
                outline = tone(primaryHue, 0.22f, 0.64f),
                outlineVariant = tone(primaryHue, 0.24f, 0.42f),
                scrim = tone(primaryHue, 0.34f, 0.13f),
                surfaceBright = tone(primaryHue, 0.28f, 0.35f),
                surfaceDim = tone(primaryHue, 0.32f, 0.175f),
                surfaceContainerLowest = tone(primaryHue, 0.32f, 0.17f),
                surfaceContainerLow = tone(primaryHue, 0.28f, 0.245f),
                surfaceContainer = tone(primaryHue, 0.28f, 0.275f),
                surfaceContainerHigh = tone(primaryHue, 0.28f, 0.315f),
                surfaceContainerHighest = tone(primaryHue, 0.28f, 0.365f)
            )
        } else {
            HarmonizedSurfaceTokens(
                background = tone(primaryHue, 0.36f, 0.88f),
                onBackground = tone(primaryHue, 0.28f, 0.11f),
                surface = tone(primaryHue, 0.36f, 0.88f),
                onSurface = tone(primaryHue, 0.28f, 0.11f),
                surfaceVariant = tone(primaryHue, 0.32f, 0.82f),
                onSurfaceVariant = tone(primaryHue, 0.24f, 0.26f),
                inverseSurface = tone(primaryHue, 0.30f, 0.20f),
                inverseOnSurface = tone(primaryHue, 0.26f, 0.92f),
                outline = tone(primaryHue, 0.24f, 0.42f),
                outlineVariant = tone(primaryHue, 0.26f, 0.65f),
                scrim = tone(primaryHue, 0.36f, 0.13f),
                surfaceBright = tone(primaryHue, 0.30f, 0.92f),
                surfaceDim = tone(primaryHue, 0.34f, 0.81f),
                surfaceContainerLowest = tone(primaryHue, 0.26f, 0.945f),
                surfaceContainerLow = tone(primaryHue, 0.30f, 0.925f),
                surfaceContainer = tone(primaryHue, 0.34f, 0.84f),
                surfaceContainerHigh = tone(primaryHue, 0.34f, 0.80f),
                surfaceContainerHighest = tone(primaryHue, 0.34f, 0.75f)
            )
        }
    }

    private fun tone(hue: Float, sat: Float, lightness: Float): Color {
        return hslToComposeColor(
            hue = hue,
            saturation = sat.coerceIn(0f, 1f),
            lightness = lightness.coerceIn(0.02f, 0.98f)
        )
    }

    private fun extractHueDegrees(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        if (delta < 0.03f) return 218f
        val rawHue = when (maxC) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
        return if (rawHue < 0f) rawHue + 360f else rawHue
    }

    private fun hslToComposeColor(hue: Float, saturation: Float, lightness: Float): Color {
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs(((hue / 60f) % 2f) - 1f))
        val m = lightness - c / 2f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}
