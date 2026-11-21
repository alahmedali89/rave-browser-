@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Builds a Google Sans Flex variable FontFamily configured for a specific optical size ([opticalSizeSp]),
 * roundness ([roundness]: 0f standard, 100f emphasized), width ([width]), and slant ([slnt]).
 *
 * Supported Google Sans Flex axes: wght, ROND, opsz, wdth, slnt (plus CRSV/FILL/GRAD/HEXP where supported).
 */
fun googleSansFlexFontFamily(
    opticalSizeSp: Float,
    roundness: Float = 0f,
    width: Float = 100f
): FontFamily {
    val supportedWeights = listOf(300, 400, 500, 600, 700, 800)
    val fonts = buildList {
        for (weightValue in supportedWeights) {
            add(
                Font(
                    resId = R.font.google_sans_flex,
                    weight = FontWeight(weightValue),
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weightValue),
                        FontVariation.Setting("opsz", opticalSizeSp),
                        FontVariation.Setting("ROND", roundness),
                        FontVariation.Setting("wdth", width),
                        FontVariation.Setting("slnt", 0f)
                    )
                )
            )
            add(
                Font(
                    resId = R.font.google_sans_flex,
                    weight = FontWeight(weightValue),
                    style = FontStyle.Italic,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weightValue),
                        FontVariation.Setting("opsz", opticalSizeSp),
                        FontVariation.Setting("ROND", roundness),
                        FontVariation.Setting("wdth", width),
                        FontVariation.Setting("slnt", -10f)
                    )
                )
            )
        }
    }
    return FontFamily(fonts)
}

/** Primary Google Sans Flex default family (16sp optical size, standard terminals). */
val GoogleSansFlexFamily: FontFamily = googleSansFlexFontFamily(opticalSizeSp = 16f, roundness = 0f)

/** Static fallback font families bundled locally in res/font. */
val GoogleSansFallbackFamily: FontFamily = FontFamily(
    Font(R.font.google_sans, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

val GoogleSansTextFamily: FontFamily = FontFamily(
    Font(R.font.google_sans_text, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

val GoogleSansDisplayFamily: FontFamily = FontFamily(
    Font(R.font.google_sans_display, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

/** Auxiliary bundled fonts: Noto Emoji, Noto Sans Myanmar (Burmese UI text), and Geist (web-clipboard). */
val NotoEmojiFamily: FontFamily = FontFamily(
    Font(R.font.noto_emoji, FontWeight.Normal)
)

val NotoSansMyanmarFamily: FontFamily = FontFamily(
    Font(R.font.noto_sans_myanmar, FontWeight.Normal)
)

val GeistFamily: FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700).map { weightValue ->
        Font(
            resId = R.font.geist,
            weight = FontWeight(weightValue),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weightValue)
            )
        )
    }
)

val JetBrainsMonoFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private fun flexStyle(
    sizeSp: Float,
    lineHeightSp: Float,
    weight: Int,
    roundness: Float
): TextStyle = TextStyle(
    fontFamily = googleSansFlexFontFamily(opticalSizeSp = sizeSp, roundness = roundness),
    fontWeight = FontWeight(weight),
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = 0.sp
)

/**
 * Full Material 3 & Expressive Type Scale built on Google Sans Flex variable font.
 * - opsz strictly equals the role's point size (sp).
 * - ROND = 0f for base roles; ROND = 100f with wght 500/600 for emphasized roles.
 * - letterSpacing = 0.0sp everywhere.
 */
val ApexTypography = Typography(
    displayLarge = flexStyle(sizeSp = 57f, lineHeightSp = 64f, weight = 400, roundness = 0f),
    displayMedium = flexStyle(sizeSp = 45f, lineHeightSp = 52f, weight = 400, roundness = 0f),
    displaySmall = flexStyle(sizeSp = 36f, lineHeightSp = 44f, weight = 400, roundness = 0f),
    headlineLarge = flexStyle(sizeSp = 32f, lineHeightSp = 40f, weight = 400, roundness = 0f),
    headlineMedium = flexStyle(sizeSp = 28f, lineHeightSp = 36f, weight = 400, roundness = 0f),
    headlineSmall = flexStyle(sizeSp = 24f, lineHeightSp = 32f, weight = 400, roundness = 0f),
    titleLarge = flexStyle(sizeSp = 22f, lineHeightSp = 28f, weight = 400, roundness = 0f),
    titleMedium = flexStyle(sizeSp = 16f, lineHeightSp = 24f, weight = 500, roundness = 0f),
    titleSmall = flexStyle(sizeSp = 14f, lineHeightSp = 20f, weight = 500, roundness = 0f),
    bodyLarge = flexStyle(sizeSp = 16f, lineHeightSp = 24f, weight = 400, roundness = 0f),
    bodyMedium = flexStyle(sizeSp = 14f, lineHeightSp = 20f, weight = 400, roundness = 0f),
    bodySmall = flexStyle(sizeSp = 12f, lineHeightSp = 16f, weight = 400, roundness = 0f),
    labelLarge = flexStyle(sizeSp = 14f, lineHeightSp = 20f, weight = 500, roundness = 0f),
    labelMedium = flexStyle(sizeSp = 12f, lineHeightSp = 16f, weight = 500, roundness = 0f),
    labelSmall = flexStyle(sizeSp = 11f, lineHeightSp = 16f, weight = 500, roundness = 0f),
    displayLargeEmphasized = flexStyle(sizeSp = 57f, lineHeightSp = 64f, weight = 500, roundness = 100f),
    displayMediumEmphasized = flexStyle(sizeSp = 45f, lineHeightSp = 52f, weight = 500, roundness = 100f),
    displaySmallEmphasized = flexStyle(sizeSp = 36f, lineHeightSp = 44f, weight = 500, roundness = 100f),
    headlineLargeEmphasized = flexStyle(sizeSp = 32f, lineHeightSp = 40f, weight = 500, roundness = 100f),
    headlineMediumEmphasized = flexStyle(sizeSp = 28f, lineHeightSp = 36f, weight = 500, roundness = 100f),
    headlineSmallEmphasized = flexStyle(sizeSp = 24f, lineHeightSp = 32f, weight = 500, roundness = 100f),
    titleLargeEmphasized = flexStyle(sizeSp = 22f, lineHeightSp = 28f, weight = 500, roundness = 100f),
    titleMediumEmphasized = flexStyle(sizeSp = 16f, lineHeightSp = 24f, weight = 600, roundness = 100f),
    titleSmallEmphasized = flexStyle(sizeSp = 14f, lineHeightSp = 20f, weight = 600, roundness = 100f),
    bodyLargeEmphasized = flexStyle(sizeSp = 16f, lineHeightSp = 24f, weight = 500, roundness = 100f),
    bodyMediumEmphasized = flexStyle(sizeSp = 14f, lineHeightSp = 20f, weight = 500, roundness = 100f),
    bodySmallEmphasized = flexStyle(sizeSp = 12f, lineHeightSp = 16f, weight = 500, roundness = 100f),
    labelLargeEmphasized = flexStyle(sizeSp = 14f, lineHeightSp = 20f, weight = 600, roundness = 100f),
    labelMediumEmphasized = flexStyle(sizeSp = 12f, lineHeightSp = 16f, weight = 600, roundness = 100f),
    labelSmallEmphasized = flexStyle(sizeSp = 11f, lineHeightSp = 16f, weight = 600, roundness = 100f)
)

/**
 * Material 3 Expressive Emphasized Type Styles for high-salience browser surfaces:
 * Address Bar, Active Tab Title, Privacy Telemetry Counters, Web-Clipboard, and Section Headers.
 */
object EmphasizedTypography {
    val displayStat = flexStyle(
        sizeSp = 34f,
        lineHeightSp = 40f,
        weight = 500,
        roundness = 100f
    )

    val headlineHero = flexStyle(
        sizeSp = 26f,
        lineHeightSp = 32f,
        weight = 500,
        roundness = 100f
    )

    val addressBarActive = flexStyle(
        sizeSp = 15f,
        lineHeightSp = 20f,
        weight = 600,
        roundness = 100f
    )

    val activeTabTitle = flexStyle(
        sizeSp = 14f,
        lineHeightSp = 20f,
        weight = 600,
        roundness = 100f
    )

    val badgeCounter = flexStyle(
        sizeSp = 11f,
        lineHeightSp = 16f,
        weight = 600,
        roundness = 100f
    )

    val webClipboard = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )

    val monoCode = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp
    )
}
