package com.example

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.ApexColorSchemeBuilder
import com.example.ui.theme.SeedPalettePreset
import com.example.ui.theme.TabGroupColorOption
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ThemeColorHarmonyTest {

    @Test
    fun allSeedPresets_avoidPureWhiteAndPureBlack_andPreserveComponentContrast() {
        val schemes = buildList {
            for (preset in SeedPalettePreset.entries) {
                add("seed-${preset.name}-light" to ApexColorSchemeBuilder.fromSeed(preset.seedColor, darkTheme = false))
                add("seed-${preset.name}-dark" to ApexColorSchemeBuilder.fromSeed(preset.seedColor, darkTheme = true))
            }
            // Simulate neutral wallpaper dynamic schemes where primary might be low-chroma or pure gray
            val neutralLight = lightColorScheme(primary = Color(0xFF707070))
            val neutralDark = darkColorScheme(primary = Color(0xFFA0A0A0))
            add("dynamic-neutral-light" to ApexColorSchemeBuilder.harmonizeSurfaceScheme(neutralLight, darkTheme = false))
            add("dynamic-neutral-dark" to ApexColorSchemeBuilder.harmonizeSurfaceScheme(neutralDark, darkTheme = true))

            val vibrantDynamicLight = lightColorScheme(primary = Color(0xFF1E8E3E))
            val vibrantDynamicDark = darkColorScheme(primary = Color(0xFF81C995))
            add("dynamic-green-light" to ApexColorSchemeBuilder.harmonizeSurfaceScheme(vibrantDynamicLight, darkTheme = false))
            add("dynamic-green-dark" to ApexColorSchemeBuilder.harmonizeSurfaceScheme(vibrantDynamicDark, darkTheme = true))
        }

        for ((label, scheme) in schemes) {
            val isDark = label.endsWith("-dark")
            val surfaceRoles = listOf(
                "background" to scheme.background,
                "surface" to scheme.surface,
                "surfaceContainerLowest" to scheme.surfaceContainerLowest,
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "surfaceContainerHighest" to scheme.surfaceContainerHighest
            )

            for ((role, color) in surfaceRoles) {
                val lum = color.luminance()
                assertTrue("$label $role luminance=$lum should never be stark white", lum < 0.94f)
                assertTrue("$label $role luminance=$lum should never be stark black", lum > 0.015f)
                assertTrue("$label $role should carry theme tint (non-zero chroma)", chromaDelta(color) > 0.02f)
            }

            // Ensure scrim is not stark black (#000000)
            assertTrue("$label scrim should not be pure black", chromaDelta(scheme.scrim) > 0.005f || scheme.scrim.luminance() > 0.004f)

            // Verify cards (surfaceContainerLow / surfaceContainer) are measurably distinct from background
            val bgLum = scheme.background.luminance()
            val cardLum = scheme.surfaceContainerLow.luminance()
            val containerLum = scheme.surfaceContainer.luminance()
            assertTrue(
                "$label surfaceContainerLow ($cardLum) must separate from background ($bgLum)",
                abs(cardLum - bgLum) >= if (isDark) 0.012f else 0.06f
            )
            assertTrue(
                "$label surfaceContainer ($containerLum) must separate from background ($bgLum)",
                abs(containerLum - bgLum) >= if (isDark) 0.020f else 0.05f
            )

            // Verify text contrast on background & surfaceContainerLow & secondaryContainer
            assertTrue(
                "$label onBackground contrast too low",
                contrastRatio(scheme.onBackground, scheme.background) >= 7.0f
            )
            assertTrue(
                "$label onSurface contrast on card too low",
                contrastRatio(scheme.onSurface, scheme.surfaceContainerLow) >= 7.0f
            )
            assertTrue(
                "$label onSurfaceVariant contrast on card too low",
                contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainerLow) >= 4.5f
            )
            assertTrue(
                "$label onSecondaryContainer contrast too low",
                contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f
            )
            assertTrue(
                "$label onPrimaryContainer contrast too low",
                contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f
            )
            assertTrue(
                "$label onTertiaryContainer contrast too low",
                contrastRatio(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f
            )
        }
    }

    @Test
    fun tabGroupTonalColors_neverProduceStarkWhiteOrBlack_andMaintainContrast() {
        for (option in TabGroupColorOption.entries) {
            for (dark in listOf(false, true)) {
                val tonal = ApexColorSchemeBuilder.groupTonalColors(option, darkTheme = dark)
                assertTrue(
                    "${option.name} dark=$dark onAccent contrast too low",
                    contrastRatio(tonal.onAccent, tonal.accent) >= 4.5f
                )
                assertTrue(
                    "${option.name} dark=$dark onContainer contrast too low",
                    contrastRatio(tonal.onContainer, tonal.container) >= 4.5f
                )
                assertTrue(
                    "${option.name} dark=$dark subtleSurface should carry tint",
                    chromaDelta(tonal.subtleSurface) > 0.03f
                )
            }
        }
    }

    private fun chromaDelta(c: Color): Float {
        val maxC = max(c.red, max(c.green, c.blue))
        val minC = min(c.red, min(c.green, c.blue))
        return maxC - minC
    }

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
