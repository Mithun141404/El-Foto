package com.contextcamera.app.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextcamera.app.viewmodel.CameraUiState
import com.contextcamera.app.viewmodel.CameraViewModel
import com.contextcamera.app.viewmodel.Status
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "CameraScreen"

// ─── Design tokens ────────────────────────────────────────────────────────────
private val Purple       = Color(0xFF7C4DFF)
private val PurpleLight  = Color(0xFF9C70FF)
private val Blue         = Color(0xFF448AFF)
private val Orange       = Color(0xFFFF6E40)
private val Red          = Color(0xFFFF1744)
private val ScanColor    = Color(0xFFB388FF)
private val GlassWhite   = Color.White.copy(alpha = 0.10f)
private val GlassBorder  = Color.White.copy(alpha = 0.18f)

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Re-bind camera when the lens flips
    DisposableEffect(uiState.useFrontCamera) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = if (uiState.useFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 1. Camera preview ───────────────────────────────────────────────
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // ── 2. Pose skeleton overlay ────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.status == Status.POSE_READY && uiState.keypoints.isNotEmpty(),
            enter = fadeIn(tween(500)),
            exit  = fadeOut(tween(300)),
        ) {
            PoseOverlay(keypoints = uiState.keypoints, modifier = Modifier.fillMaxSize())
        }

        val onPoseClickAction = {
            viewModel.onCapturing()
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes  = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val maxDim = 400f
                            val scale = maxDim / maxOf(bitmap.width, bitmap.height)
                            val resized = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                            } else bitmap
                            
                            val out = ByteArrayOutputStream()
                            resized.compress(Bitmap.CompressFormat.JPEG, 50, out)
                            viewModel.onImageCaptured(out.toByteArray())
                        } catch (e: Exception) {
                            image.close()
                            viewModel.onError("Frame processing failed: ${e.message}")
                        }
                    }
                    override fun onError(ex: ImageCaptureException) {
                        viewModel.onError("Capture failed: ${ex.message}")
                    }
                },
            )
        }

        // ── 3. AI scanning animation ─────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.status == Status.CAPTURING || uiState.status == Status.ANALYZING,
            enter = fadeIn(tween(150)),
            exit  = fadeOut(tween(350)),
        ) {
            ScanningOverlay(status = uiState.status)
        }

        // ── 4. Top Controls (Scan / Clear) ───────────────────────────────────
        TopControls(
            uiState = uiState,
            onPoseClick = onPoseClickAction,
            onClearClick = { viewModel.clearPose() }
        )

        // ── 5. Info Banner (Scene & Pose names) ──────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 140.dp) // Offset above bottom controls
            ) {
                InfoBanner(uiState = uiState)
            }
        }

        // ── 5. Bottom controls ───────────────────────────────────────────────
        BottomControls(
            uiState      = uiState,
            onShutterClick = { capturePhoto(context, imageCapture) },
            onFlipClick    = { viewModel.toggleCamera() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI scanning overlay — animated scan line + grid + viewfinder corners
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScanningOverlay(status: Status) {
    val inf = rememberInfiniteTransition(label = "scan")

    val scanY by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "scanY",
    )
    val pulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    val dotPhase by inf.animateFloat(
        0f, 3f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )

    val dots = ".".repeat(dotPhase.toInt() + 1)
    val label = when (status) {
        Status.CAPTURING -> "Capturing frame$dots"
        Status.ANALYZING -> "Analyzing scene$dots"
        else             -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val yPx = scanY * h

            // Subtle purple grid
            val cols = 7
            val rows = 13
            repeat(cols + 1) { c ->
                drawLine(
                    color       = Purple.copy(alpha = 0.14f),
                    start       = Offset(c * w / cols, 0f),
                    end         = Offset(c * w / cols, h),
                    strokeWidth = 1f,
                )
            }
            repeat(rows + 1) { r ->
                drawLine(
                    color       = Purple.copy(alpha = 0.14f),
                    start       = Offset(0f, r * h / rows),
                    end         = Offset(w, r * h / rows),
                    strokeWidth = 1f,
                )
            }

            // Glow trail above the scan line
            val trailTop = maxOf(0f, yPx - 160f)
            val trailH   = yPx - trailTop
            if (trailH > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, ScanColor.copy(alpha = 0.28f * pulse)),
                        startY = trailTop,
                        endY   = yPx,
                    ),
                    topLeft = Offset(0f, trailTop),
                    size    = Size(w, trailH),
                )
            }

            // Outer scan glow (wide, dim)
            drawLine(
                color       = ScanColor.copy(alpha = 0.35f * pulse),
                start       = Offset(0f, yPx),
                end         = Offset(w, yPx),
                strokeWidth = 14f,
                cap         = StrokeCap.Round,
            )
            // Core scan line
            drawLine(
                color       = ScanColor.copy(alpha = 0.95f),
                start       = Offset(0f, yPx),
                end         = Offset(w, yPx),
                strokeWidth = 3f,
                cap         = StrokeCap.Round,
            )
            // Bright center highlight
            drawLine(
                color       = Color.White.copy(alpha = 0.65f),
                start       = Offset(w * 0.25f, yPx),
                end         = Offset(w * 0.75f, yPx),
                strokeWidth = 1.5f,
                cap         = StrokeCap.Round,
            )

            // Viewfinder corner brackets
            val m  = 72f
            val l  = 56f
            val lw = 5f
            val bc = PurpleLight.copy(alpha = 0.9f)
            // Top-left
            drawLine(bc, Offset(m, m),         Offset(m + l, m),     lw, cap = StrokeCap.Round)
            drawLine(bc, Offset(m, m),         Offset(m, m + l),     lw, cap = StrokeCap.Round)
            // Top-right
            drawLine(bc, Offset(w - m, m),     Offset(w - m - l, m), lw, cap = StrokeCap.Round)
            drawLine(bc, Offset(w - m, m),     Offset(w - m, m + l), lw, cap = StrokeCap.Round)
            // Bottom-left
            drawLine(bc, Offset(m, h - m),     Offset(m + l, h - m), lw, cap = StrokeCap.Round)
            drawLine(bc, Offset(m, h - m),     Offset(m, h - m - l), lw, cap = StrokeCap.Round)
            // Bottom-right
            drawLine(bc, Offset(w - m, h - m), Offset(w - m - l, h - m), lw, cap = StrokeCap.Round)
            drawLine(bc, Offset(w - m, h - m), Offset(w - m, h - m - l), lw, cap = StrokeCap.Round)
        }

        // Central AI label
        Column(
            modifier                = Modifier.align(Alignment.Center),
            horizontalAlignment     = Alignment.CenterHorizontally,
        ) {
            Text(
                text          = "AI VISION",
                color         = PurpleLight,
                fontSize      = 11.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 5.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = label,
                color      = Color.White.copy(alpha = pulse),
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top info banner — scene description + pose chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopControls(
    uiState: CameraUiState,
    onPoseClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val isBusy = uiState.status == Status.CAPTURING || uiState.status == Status.ANALYZING
    val isPoseReady = uiState.status == Status.POSE_READY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 18.dp, end = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(0.4f))
                        .clickable { if (isPoseReady) onClearClick() else onPoseClick() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPoseReady) "CLEAR" else "SCAN",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(uiState: CameraUiState) {
    val showBanner = uiState.sceneName.isNotEmpty() || (uiState.status == Status.ERROR && uiState.errorMessage != null)
    
    AnimatedVisibility(
        visible = showBanner,
        enter   = fadeIn(tween(400)),
        exit    = fadeOut(tween(300)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
                // Scene name card
                if (uiState.sceneName.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0x99000000), Color(0xBB1A0040))))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📍", fontSize = 11.sp)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "SCENE DETECTED",
                                    color = PurpleLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = uiState.sceneName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Pose name chip
                if (uiState.poseName.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.horizontalGradient(listOf(Purple.copy(0.85f), Blue.copy(0.85f))))
                            .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "✨  ${uiState.poseName.uppercase()}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                // Error card
                if (uiState.status == Status.ERROR && uiState.errorMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Red.copy(alpha = 0.82f))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "⚠  ${uiState.errorMessage}",
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls — redesigned with gradient POSE button and premium feel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomControls(
    uiState: CameraUiState,
    onShutterClick: () -> Unit,
    onFlipClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Classic camera control panel (solid black/translucent strip)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.6f))
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Flip Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(0.6f))
                        .clickable { onFlipClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center: Classic Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onShutterClick() }
                )

                // Right: Gallery preview window (placeholder)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Could load recent image here; using a placeholder for now
                    Text("🖼", fontSize = 20.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Save photo helper
// ─────────────────────────────────────────────────────────────────────────────

private fun capturePhoto(context: Context, imageCapture: ImageCapture) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "ContextCamera_$name")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ContextCamera")
    }

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        .build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(TAG, "Photo saved: ${output.savedUri}")
                Toast.makeText(context, "Photo saved! 📸", Toast.LENGTH_SHORT).show()
            }
            override fun onError(ex: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", ex)
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        },
    )
}
