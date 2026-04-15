package com.contextcamera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.contextcamera.app.network.Keypoint

/**
 * Skeletal pose overlay rendered on a Compose Canvas.
 * Draws a glowing white wireframe of 33 BlazePose keypoints.
 *
 * Keypoint indices (BlazePose 33-point):
 *  0: nose
 *  1-3: left eye (inner, center, outer)
 *  4-6: right eye (inner, center, outer)
 *  7: left ear, 8: right ear
 *  9: mouth left, 10: mouth right
 *  11: left shoulder, 12: right shoulder
 *  13: left elbow, 14: right elbow
 *  15: left wrist, 16: right wrist
 *  17: left pinky, 18: right pinky
 *  19: left index, 20: right index
 *  21: left thumb, 22: right thumb
 *  23: left hip, 24: right hip
 *  25: left knee, 26: right knee
 *  27: left ankle, 28: right ankle
 *  29: left heel, 30: right heel
 *  31: left foot index, 32: right foot index
 */
@Composable
fun PoseOverlay(
    keypoints: List<Keypoint>,
    modifier: Modifier = Modifier,
) {
    if (keypoints.size != 33) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Convert normalized coordinates to pixel positions
        val points = keypoints.map { kp ->
            Offset(kp.x * w, kp.y * h)
        }

        // Define skeleton connections as pairs of keypoint indices
        val connections = listOf(
            // Face
            0 to 1, 1 to 2, 2 to 3,     // nose → left eye
            0 to 4, 4 to 5, 5 to 6,     // nose → right eye
            3 to 7,                       // left eye outer → left ear
            6 to 8,                       // right eye outer → right ear
            9 to 10,                      // mouth

            // Torso
            11 to 12,                     // shoulders
            11 to 23, 12 to 24,          // shoulders → hips
            23 to 24,                     // hips

            // Left arm
            11 to 13, 13 to 15,          // shoulder → elbow → wrist
            15 to 17, 15 to 19, 15 to 21, // wrist → fingers

            // Right arm
            12 to 14, 14 to 16,          // shoulder → elbow → wrist
            16 to 18, 16 to 20, 16 to 22, // wrist → fingers

            // Left leg
            23 to 25, 25 to 27,          // hip → knee → ankle
            27 to 29, 27 to 31,          // ankle → heel, ankle → foot

            // Right leg
            24 to 26, 26 to 28,          // hip → knee → ankle
            28 to 30, 28 to 32,          // ankle → heel, ankle → foot
        )

        // Draw glow layer (wider, more transparent)
        for ((a, b) in connections) {
            drawGlowLine(points[a], points[b])
        }

        // Draw main skeleton lines
        for ((a, b) in connections) {
            drawSkeletonLine(points[a], points[b])
        }

        // Draw joint circles
        // Major joints get bigger dots
        val majorJoints = setOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
        for ((i, point) in points.withIndex()) {
            val radius = if (i in majorJoints) 10f else 6f
            // Glow
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = radius + 8f,
                center = point,
            )
            // Main dot
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius,
                center = point,
            )
        }
    }
}

/**
 * Draw a glowing background line (wider, semi-transparent).
 */
private fun DrawScope.drawGlowLine(start: Offset, end: Offset) {
    drawLine(
        color = Color.White.copy(alpha = 0.08f),
        start = start,
        end = end,
        strokeWidth = 18f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White.copy(alpha = 0.15f),
        start = start,
        end = end,
        strokeWidth = 10f,
        cap = StrokeCap.Round,
    )
}

/**
 * Draw the main skeleton line.
 */
private fun DrawScope.drawSkeletonLine(start: Offset, end: Offset) {
    drawLine(
        color = Color.White.copy(alpha = 0.75f),
        start = start,
        end = end,
        strokeWidth = 4f,
        cap = StrokeCap.Round,
    )
}
