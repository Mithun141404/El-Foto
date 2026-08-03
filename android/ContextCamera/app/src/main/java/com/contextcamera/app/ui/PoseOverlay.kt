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

// ─── Body-part color palette ──────────────────────────────────────────────────
private val C_FACE      = Color(0xFF00E5FF)   // Cyan
private val C_TORSO     = Color(0xFFE040FB)   // Purple
private val C_ARM_LEFT  = Color(0xFFFF6D00)   // Orange
private val C_ARM_RIGHT = Color(0xFF40C4FF)   // Light blue
private val C_LEG_LEFT  = Color(0xFF69F0AE)   // Green
private val C_LEG_RIGHT = Color(0xFFFF4081)   // Pink

/**
 * Skeleton connection: two keypoint indices + the color for that body segment.
 */
private data class Bone(val a: Int, val b: Int, val color: Color)

/**
 * All 33-point BlazePose connections, grouped by body part and color-coded.
 *
 * Index reference:
 *  0=nose | 1-3=left eye | 4-6=right eye | 7=left ear | 8=right ear
 *  9=mouth_l | 10=mouth_r | 11=l_shoulder | 12=r_shoulder
 *  13=l_elbow | 14=r_elbow | 15=l_wrist | 16=r_wrist
 *  17=l_pinky | 18=r_pinky | 19=l_index | 20=r_index | 21=l_thumb | 22=r_thumb
 *  23=l_hip | 24=r_hip | 25=l_knee | 26=r_knee | 27=l_ankle | 28=r_ankle
 *  29=l_heel | 30=r_heel | 31=l_foot | 32=r_foot
 */
private val BONES = listOf(
    // Face (cyan)
    Bone(0,  1,  C_FACE), Bone(1,  2,  C_FACE), Bone(2,  3,  C_FACE),
    Bone(0,  4,  C_FACE), Bone(4,  5,  C_FACE), Bone(5,  6,  C_FACE),
    Bone(3,  7,  C_FACE), Bone(6,  8,  C_FACE), Bone(9,  10, C_FACE),

    // Torso (purple)
    Bone(11, 12, C_TORSO),
    Bone(11, 23, C_TORSO), Bone(12, 24, C_TORSO),
    Bone(23, 24, C_TORSO),

    // Left arm (orange)
    Bone(11, 13, C_ARM_LEFT), Bone(13, 15, C_ARM_LEFT),
    Bone(15, 17, C_ARM_LEFT), Bone(15, 19, C_ARM_LEFT), Bone(15, 21, C_ARM_LEFT),

    // Right arm (light blue)
    Bone(12, 14, C_ARM_RIGHT), Bone(14, 16, C_ARM_RIGHT),
    Bone(16, 18, C_ARM_RIGHT), Bone(16, 20, C_ARM_RIGHT), Bone(16, 22, C_ARM_RIGHT),

    // Left leg (green)
    Bone(23, 25, C_LEG_LEFT), Bone(25, 27, C_LEG_LEFT),
    Bone(27, 29, C_LEG_LEFT), Bone(27, 31, C_LEG_LEFT),

    // Right leg (pink)
    Bone(24, 26, C_LEG_RIGHT), Bone(26, 28, C_LEG_RIGHT),
    Bone(28, 30, C_LEG_RIGHT), Bone(28, 32, C_LEG_RIGHT),
)

/** Returns the display color for a joint based on its keypoint index. */
private fun jointColor(index: Int): Color = when (index) {
    in 0..10            -> C_FACE
    11, 12, 23, 24      -> C_TORSO
    13, 15, 17, 19, 21  -> C_ARM_LEFT
    14, 16, 18, 20, 22  -> C_ARM_RIGHT
    25, 27, 29, 31      -> C_LEG_LEFT
    26, 28, 30, 32      -> C_LEG_RIGHT
    else                -> Color.White
}

/**
 * Skeletal pose overlay rendered on a Compose Canvas.
 * Draws a color-coded, glowing wireframe of 33 BlazePose keypoints,
 * grouped and colored by body segment for a premium look.
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
        val pts = keypoints.map { kp -> Offset(kp.x * w, kp.y * h) }

        // Pass 1: wide diffuse glow behind each bone
        for (bone in BONES) {
            drawGlow(pts[bone.a], pts[bone.b], bone.color)
        }

        // Pass 2: crisp main bone line
        for (bone in BONES) {
            drawBone(pts[bone.a], pts[bone.b], bone.color)
        }

        // Pass 3: joint circles
        val majorJoints = setOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
        for ((i, pt) in pts.withIndex()) {
            val color  = jointColor(i)
            val radius = if (i in majorJoints) 11f else 7f
            // Soft outer glow
            drawCircle(color = color.copy(alpha = 0.18f), radius = radius + 10f, center = pt)
            // Mid glow ring
            drawCircle(color = color.copy(alpha = 0.35f), radius = radius + 4f,  center = pt)
            // Solid joint
            drawCircle(color = color.copy(alpha = 0.92f), radius = radius,        center = pt)
            // Bright specular dot
            drawCircle(color = Color.White.copy(alpha = 0.7f), radius = radius * 0.4f, center = pt)
        }
    }
}

/** Wide diffuse glow layer behind each bone. */
private fun DrawScope.drawGlow(start: Offset, end: Offset, color: Color) {
    drawLine(color = color.copy(alpha = 0.06f), start = start, end = end, strokeWidth = 26f, cap = StrokeCap.Round)
    drawLine(color = color.copy(alpha = 0.14f), start = start, end = end, strokeWidth = 14f, cap = StrokeCap.Round)
}

/** Crisp colored bone line. */
private fun DrawScope.drawBone(start: Offset, end: Offset, color: Color) {
    drawLine(color = color.copy(alpha = 0.80f), start = start, end = end, strokeWidth = 4.5f, cap = StrokeCap.Round)
}
