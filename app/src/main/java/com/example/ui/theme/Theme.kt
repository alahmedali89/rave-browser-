package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

val LocalApexDarkTheme = compositionLocalOf<Boolean?> { null }

/**
 * Software-canvas-safe press/hover/focus indication that uses pure Compose DrawScope primitives
 * instead of platform `android.graphics.drawable.RippleDrawable` (`STYLE_PATTERNED`), preventing
 * `E/RippleDrawable: The RippleDrawable.STYLE_PATTERNED animation is not supported for a non-hardware accelerated Canvas`.
 */
private data class CanvasSafeIndication(
    private val tintColor: Color
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return CanvasSafeIndicationNode(interactionSource, tintColor)
    }

    private class CanvasSafeIndicationNode(
        private val interactionSource: InteractionSource,
        private val tintColor: Color
    ) : Modifier.Node(), DrawModifierNode {
        private var isPressed = false
        private var isHovered = false
        private var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                val pressInteractions = mutableSetOf<PressInteraction.Press>()
                val hoverInteractions = mutableSetOf<HoverInteraction.Enter>()
                val focusInteractions = mutableSetOf<FocusInteraction.Focus>()
                interactionSource.interactions.collectLatest { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressInteractions.add(interaction)
                        is PressInteraction.Release -> pressInteractions.remove(interaction.press)
                        is PressInteraction.Cancel -> pressInteractions.remove(interaction.press)
                        is HoverInteraction.Enter -> hoverInteractions.add(interaction)
                        is HoverInteraction.Exit -> hoverInteractions.remove(interaction.enter)
                        is FocusInteraction.Focus -> focusInteractions.add(interaction)
                        is FocusInteraction.Unfocus -> focusInteractions.remove(interaction.focus)
                    }
                    val newPressed = pressInteractions.isNotEmpty()
                    val newHovered = hoverInteractions.isNotEmpty()
                    val newFocused = focusInteractions.isNotEmpty()
                    if (isPressed != newPressed || isHovered != newHovered || isFocused != newFocused) {
                        isPressed = newPressed
                        isHovered = newHovered
                        isFocused = newFocused
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            when {
                isPressed -> drawRect(color = tintColor.copy(alpha = 0.12f))
                isFocused -> drawRect(color = tintColor.copy(alpha = 0.08f))
                isHovered -> drawRect(color = tintColor.copy(alpha = 0.06f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ApexBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedPreset: SeedPalettePreset = SeedPalettePreset.ELECTRIC_COBALT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = rememberSystemReducedMotion()

    val colorScheme = remember(dynamicColor, darkTheme, seedPreset, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val dynamicBase = if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
                ApexColorSchemeBuilder.harmonizeSurfaceScheme(dynamicBase, darkTheme)
            }
            else -> ApexColorSchemeBuilder.fromSeed(seedPreset.seedColor, darkTheme)
        }
    }

    val canvasSafeIndication = remember(colorScheme.onSurface) {
        CanvasSafeIndication(tintColor = colorScheme.onSurface)
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalApexDarkTheme provides darkTheme,
        LocalRippleConfiguration provides null
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = ApexTypography,
            shapes = ApexShapes,
            motionScheme = MotionScheme.expressive()
        ) {
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                LocalIndication provides canvasSafeIndication,
                content = content
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    ApexBrowserTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
