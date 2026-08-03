package com.contextcamera.app.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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

        // ── 3. AI scanning animation ─────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.status == Status.CAPTURING || uiState.status == Status.ANALYZING,
            enter = fadeIn(tween(150)),
            exit  = fadeOut(tween(350)),
        ) {
            ScanningOverlay(status = uiState.status)
        }

        // ── 4. Scene / pose info banner ──────────────────────────────────────
        TopInfoBanner(uiState = uiState)

        // ── 5. Bottom controls ───────────────────────────────────────────────
        BottomControls(
            uiState      = uiState,
            onPoseClick  = {
                viewModel.onCapturing()
                // Capture an in-memory JPEG frame — never saved to gallery
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                // ImageCapture delivers JPEG via planes[0]
                                val buffer = image.planes[0].buffer
                                val bytes  = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                image.close()
                                viewModel.onImageCaptured(bytes)
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
            },
            onShutterClick = { capturePhoto(context, imageCapture) },
            onFlipClick    = { viewModel.toggleCamera() },
            onClearClick   = { viewModel.clearPose() },
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
            Text(text = "🤖", fontSize = 54.sp)
            Spacer(Modifier.height(12.dp))
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
private fun TopInfoBanner(uiState: CameraUiState) {
    val showBanner = uiState.sceneName.isNotEmpty() ||
            (uiState.status == Status.ERROR && uiState.errorMessage != null)

    AnimatedVisibility(
        visible = showBanner,
        enter   = fadeIn(tween(400)),
        exit    = fadeOut(tween(300)),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(top = 54.dp, start = 18.dp, end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Scene name card ──────────────────────────────────────────────
            if (uiState.sceneName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0x99000000), Color(0xBB1A0040)),
                            )
                        )
                        .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text          = "📍",
                                fontSize      = 11.sp,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text          = "SCENE DETECTED",
                                color         = PurpleLight,
                                fontSize      = 10.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text     = uiState.sceneName,
                            color    = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // ── Pose name chip ───────────────────────────────────────────────
            if (uiState.poseName.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Purple.copy(0.85f), Blue.copy(0.85f)))
                        )
                        .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text          = "✨  ${uiState.poseName.uppercase()}",
                        color         = Color.White,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }

            // ── Error card ───────────────────────────────────────────────────
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
                        text      = "⚠  ${uiState.errorMessage}",
                        color     = Color.White,
                        fontSize  = 12.sp,
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
    uiState      : CameraUiState,
    onPoseClick  : () -> Unit,
    onShutterClick: () -> Unit,
    onFlipClick  : () -> Unit,
    onClearClick : () -> Unit,
) {
    val inf = rememberInfiniteTransition(label = "idle_pulse")
    val idlePulse by inf.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "idlePulse",
    )

    val isPoseReady = uiState.status == Status.POSE_READY
    val isBusy      = uiState.status == Status.CAPTURING || uiState.status == Status.ANALYZING
    val isEnabled   = uiState.status == Status.IDLE || isPoseReady || uiState.status == Status.ERROR

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // Panel background — fade from transparent to dark
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(0.80f), Color.Black.copy(0.92f)),
                    )
                )
                .padding(horizontal = 28.dp, vertical = 26.dp),
        ) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement= Arrangement.SpaceBetween,
                verticalAlignment    = Alignment.CenterVertically,
            ) {

                // ── Flip / lens toggle ───────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(GlassWhite)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { onFlipClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⟲", color = Color.White, fontSize = 26.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text          = "FLIP",
                        color         = Color.White.copy(0.5f),
                        fontSize      = 10.sp,
                        letterSpacing = 1.sp,
                    )
                }

                // ── POSE / CLEAR button ──────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBusy) {
                        // Spinner while AI is working
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .width(136.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(GlassWhite)
                                .border(1.dp, PurpleLight.copy(0.4f), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color        = PurpleLight,
                                modifier     = Modifier.size(22.dp),
                                strokeWidth  = 2.5.dp,
                            )
                        }
                    } else {
                        val gradient = if (isPoseReady)
                            Brush.horizontalGradient(listOf(Orange, Red))
                        else
                            Brush.horizontalGradient(listOf(Purple, Blue))

                        val idleBorder = if (uiState.status == Status.IDLE)
                            Modifier.border(
                                (2.5f * idlePulse).dp,
                                Purple.copy(alpha = idlePulse * 0.55f),
                                RoundedCornerShape(28.dp),
                            )
                        else Modifier

                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .width(136.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (isEnabled) gradient
                                    else Brush.horizontalGradient(
                                        listOf(Color.DarkGray.copy(0.5f), Color.DarkGray.copy(0.5f))
                                    )
                                )
                                .then(idleBorder)
                                .clickable(enabled = isEnabled) {
                                    if (isPoseReady) onClearClick() else onPoseClick()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text          = if (isPoseReady) "CLEAR" else "POSE",
                                color         = Color.White,
                                fontSize      = 17.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(7.dp))
                    Text(
                        text          = when {
                            isBusy      -> "AI THINKING..."
                            isPoseReady -> "TAP TO CLEAR"
                            else        -> "SUGGEST POSE"
                        },
                        color         = Color.White.copy(0.45f),
                        fontSize      = 9.sp,
                        letterSpacing = 0.8.sp,
                    )
                }

                // ── Shutter button ───────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(0.35f), CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onShutterClick() },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text          = "SHOOT",
                        color         = Color.White.copy(0.5f),
                        fontSize      = 10.sp,
                        letterSpacing = 1.sp,
                    )
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
