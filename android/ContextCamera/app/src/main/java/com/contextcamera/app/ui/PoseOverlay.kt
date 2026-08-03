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

// ─── Silhouette style ──────────────────────────────────────────────────────────
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas

private val C_SILHOUETTE = android.graphics.Color.WHITE

/**
 * Skeleton connection with varying thickness to simulate body volume.
 */
private data class Bone(val a: Int, val b: Int, val thickness: Float)

/**
 * 33-point BlazePose connections styled as a thick silhouette.
 */
private val BONES = listOf(
    // Face/Head (thick to form a head shape)
    Bone(0,  1,  80f), Bone(1,  2,  80f), Bone(2,  3,  80f),
    Bone(0,  4,  80f), Bone(4,  5,  80f), Bone(5,  6,  80f),
    Bone(3,  7,  80f), Bone(6,  8,  80f), Bone(9,  10, 80f),

    // Torso (very thick)
    Bone(11, 12, 100f),
    Bone(11, 23, 110f), Bone(12, 24, 110f),
    Bone(23, 24, 110f),

    // Arms
    Bone(11, 13, 50f), Bone(13, 15, 45f),
    Bone(15, 17, 30f), Bone(15, 19, 30f), Bone(15, 21, 30f),
    
    Bone(12, 14, 50f), Bone(14, 16, 45f),
    Bone(16, 18, 30f), Bone(16, 20, 30f), Bone(16, 22, 30f),

    // Legs
    Bone(23, 25, 65f), Bone(25, 27, 55f),
    Bone(27, 29, 35f), Bone(27, 31, 35f),

    Bone(24, 26, 65f), Bone(26, 28, 55f),
    Bone(28, 30, 35f), Bone(28, 32, 35f),
)

/**
 * Skeletal pose overlay rendered on a Compose Canvas.
 * Draws a true hollow outline (chalk outline) of the pose.
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

        val masterPath = Path()

        // Create a solid shape (pill) for each bone and merge them all into a single unified polygon
        for (bone in BONES) {
            val bonePath = Path()
            bonePath.moveTo(pts[bone.a].x, pts[bone.a].y)
            bonePath.lineTo(pts[bone.b].x, pts[bone.b].y)

            val p = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = bone.thickness
            }

            val fillPath = Path()
            p.getFillPath(bonePath, fillPath)

            masterPath.op(fillPath, Path.Op.UNION)
        }

        // Draw the unified boundary with a thin stroke and a heavy CornerPathEffect to make it completely curvy
        val drawPaint = Paint().apply {
            color = C_SILHOUETTE
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
            pathEffect = CornerPathEffect(70f) // Curves all sharp internal concave intersections
        }

        drawContext.canvas.nativeCanvas.drawPath(masterPath, drawPaint)
    }
}

