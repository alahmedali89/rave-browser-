package com.example.ui.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom Material Symbols vectors fetched directly from Google Fonts Material Symbols Outlined.
 */
object ApexIcons {
    private var _motionSensorActive: ImageVector? = null

    /**
     * Exact Material Symbols Outlined: motion_sensor_active
     * (opsz=24, wght=400, FILL=0, GRAD=0).
     */
    val MotionSensorActive: ImageVector
        get() {
            if (_motionSensorActive != null) {
                return _motionSensorActive!!
            }
            _motionSensorActive = ImageVector.Builder(
                name = "motion_sensor_active",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1f,
                    stroke = null,
                    strokeAlpha = 1f,
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Bevel,
                    strokeLineMiter = 1f,
                    pathFillType = PathFillType.Companion.NonZero,
                ) {
                    moveTo(4f, 22f)
                    quadTo(3.18f, 22f, 2.59f, 21.41f)
                    reflectiveQuadTo(2f, 20f)
                    verticalLineTo(16f)
                    horizontalLineTo(4f)
                    verticalLineToRelative(4f)
                    horizontalLineTo(8f)
                    verticalLineToRelative(2f)
                    horizontalLineTo(4f)
                    close()
                    moveTo(2f, 8f)
                    verticalLineTo(4f)
                    quadTo(2f, 3.17f, 2.59f, 2.59f)
                    reflectiveQuadTo(4f, 2f)
                    horizontalLineTo(8f)
                    verticalLineTo(4f)
                    horizontalLineTo(4f)
                    verticalLineTo(8f)
                    horizontalLineTo(2f)
                    close()
                    moveToRelative(9f, 10.9f)
                    quadTo(8.7f, 18.55f, 7.08f, 16.93f)
                    reflectiveQuadTo(5.1f, 13f)
                    horizontalLineToRelative(2f)
                    quadToRelative(0.3f, 1.47f, 1.36f, 2.54f)
                    reflectiveQuadTo(11f, 16.9f)
                    verticalLineToRelative(2f)
                    close()
                    moveTo(5.1f, 11f)
                    quadTo(5.45f, 8.7f, 7.08f, 7.06f)
                    reflectiveQuadTo(11f, 5.1f)
                    verticalLineToRelative(2f)
                    quadTo(9.53f, 7.4f, 8.46f, 8.46f)
                    reflectiveQuadTo(7.1f, 11f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(12f, 14f)
                    quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
                    reflectiveQuadTo(10f, 12f)
                    quadToRelative(0f, -0.85f, 0.59f, -1.43f)
                    reflectiveQuadTo(12f, 10f)
                    quadToRelative(0.85f, 0f, 1.43f, 0.57f)
                    reflectiveQuadTo(14f, 12f)
                    quadToRelative(0f, 0.82f, -0.57f, 1.41f)
                    reflectiveQuadTo(12f, 14f)
                    close()
                    moveToRelative(1f, 4.9f)
                    verticalLineToRelative(-2f)
                    quadToRelative(1.48f, -0.3f, 2.54f, -1.36f)
                    quadTo(16.6f, 14.48f, 16.9f, 13f)
                    horizontalLineToRelative(2f)
                    quadToRelative(-0.33f, 2.3f, -1.96f, 3.93f)
                    reflectiveQuadTo(13f, 18.9f)
                    close()
                    moveTo(16.9f, 11f)
                    quadTo(16.6f, 9.52f, 15.54f, 8.46f)
                    reflectiveQuadTo(13f, 7.1f)
                    verticalLineToRelative(-2f)
                    quadToRelative(2.3f, 0.35f, 3.94f, 1.98f)
                    reflectiveQuadTo(18.9f, 11f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(16f, 22f)
                    verticalLineTo(20f)
                    horizontalLineToRelative(4f)
                    verticalLineTo(16f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(4f)
                    quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                    reflectiveQuadTo(20f, 22f)
                    horizontalLineTo(16f)
                    close()
                    moveTo(20f, 8f)
                    verticalLineTo(4f)
                    horizontalLineTo(16f)
                    verticalLineTo(2f)
                    horizontalLineToRelative(4f)
                    quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                    reflectiveQuadTo(22f, 4f)
                    verticalLineTo(8f)
                    horizontalLineTo(20f)
                    close()
                }
            }.build()
            return _motionSensorActive!!
        }
}
