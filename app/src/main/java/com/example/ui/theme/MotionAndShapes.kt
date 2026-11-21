package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tracks whether the Android system's reduced-motion / animator duration scale is disabled.
 */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        }.getOrDefault(false)
    }
}

/**
 * Material 3 Expressive spring physics:
 * - Spatial motion: dampingRatio = 0.9f, stiffness = 700f
 * - Effects motion (color/opacity): dampingRatio = 1.0f
 * Honors system reduced-motion setting by falling back to crossfade/snap specs.
 */
object ApexMotion {
    fun <T> spatialSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = 700f
    )

    fun <T> effectsSpring(): SpringSpec<T> = spring(
        dampingRatio = 1.0f
    )

    @Composable
    fun <T> spatialSpec(): AnimationSpec<T> {
        return if (LocalReducedMotion.current) snap() else spatialSpring()
    }

    @Composable
    fun <T> effectsSpec(): AnimationSpec<T> {
        return if (LocalReducedMotion.current) tween(durationMillis = 120) else effectsSpring()
    }

    /**
     * Crisp crossfade transition for switching between tabs without tearing hardware WebView surfaces.
     */
    fun sharedAxisTabTransition(forward: Boolean, reducedMotion: Boolean): ContentTransform {
        return if (reducedMotion) {
            fadeIn(tween(90)) togetherWith fadeOut(tween(70))
        } else {
            fadeIn(tween(115)) togetherWith fadeOut(tween(85))
        }
    }

    /**
     * Crisp opaque overlay transition avoiding offscreen full-screen scale resampling over WebView.
     */
    fun containerTransformSpec(reducedMotion: Boolean): Pair<androidx.compose.animation.EnterTransition, androidx.compose.animation.ExitTransition> {
        return if (reducedMotion) {
            fadeIn(tween(90)) to fadeOut(tween(70))
        } else {
            fadeIn(tween(130)) to fadeOut(tween(100))
        }
    }
}

/**
 * Expanded Material 3 Expressive Shape Scale.
 */
val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/**
 * Morphs corner radius between collapsed/inactive and expanded/active states using spatial springs.
 */
@Composable
fun rememberMorphingCornerShape(
    targetRadius: Dp,
    label: String = "ExpressiveCornerMorph"
): Shape {
    val reducedMotion = LocalReducedMotion.current
    val animatedRadius: State<Dp> = animateDpAsState(
        targetValue = targetRadius,
        animationSpec = if (reducedMotion) snap() else ApexMotion.spatialSpring(),
        label = label
    )
    return remember(animatedRadius) {
        RoundedCornerShape(
            object : CornerSize {
                override fun toPx(shapeSize: Size, density: Density): Float {
                    return with(density) { animatedRadius.value.toPx() }
                }
            }
        )
    }
}
