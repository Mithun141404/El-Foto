package com.contextcamera.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contextcamera.app.network.ApiClient
import com.contextcamera.app.network.Keypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * UI state for the camera screen.
 *
 * [sceneName] is a vivid AI-generated description of the real scene
 * (e.g. "sun-drenched golden beach at sunset") rather than a fixed category.
 */
data class CameraUiState(
    val status: Status = Status.IDLE,
    val sceneName: String = "",
    val poseName: String = "",
    val keypoints: List<Keypoint> = emptyList(),
    val errorMessage: String? = null,
    val useFrontCamera: Boolean = false,
)

enum class Status {
    IDLE,       // Camera running, no overlay
    CAPTURING,  // Taking the in-memory snapshot
    ANALYZING,  // Gemini Vision is processing the frame
    POSE_READY, // Pose overlay is visible
    ERROR,      // Something went wrong
}

/**
 * ViewModel managing the camera screen state.
 *
 * Scene recognition is now fully handled by Gemini Vision AI —
 * the raw JPEG frame is sent directly to the backend, which
 * looks at the actual image to identify the scene and generate a pose.
 * No on-device ML Kit or hardcoded keyword mappings involved.
 */
class CameraViewModel : ViewModel() {

    companion object {
        private const val TAG = "CameraViewModel"
    }

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** Called when the user taps POSE — camera is snapping a frame. */
    fun onCapturing() {
        _uiState.value = _uiState.value.copy(
            status = Status.CAPTURING,
            errorMessage = null,
        )
    }

    /**
     * Called with the raw JPEG bytes captured from ImageCapture.
     * Builds a multipart request and sends it to Gemini Vision via the backend.
     */
    fun onImageCaptured(imageBytes: ByteArray) {
        _uiState.value = _uiState.value.copy(
            status = Status.ANALYZING,
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                Log.d(TAG, "Sending ${imageBytes.size} bytes to Gemini Vision")
                val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("image", "frame.jpg", requestBody)
                val response = ApiClient.service.analyzeAndPose(part)

                Log.d(TAG, "Scene: '${response.sceneName}' | Pose: '${response.poseName}'")

                _uiState.value = _uiState.value.copy(
                    status = Status.POSE_READY,
                    sceneName = response.sceneName,
                    poseName = response.poseName,
                    keypoints = response.keypoints,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision request failed", e)
                _uiState.value = _uiState.value.copy(
                    status = Status.ERROR,
                    errorMessage = "AI analysis failed: ${e.message}",
                )
            }
        }
    }

    /** Set an error from the image capture layer. */
    fun onError(message: String?) {
        _uiState.value = _uiState.value.copy(
            status = Status.ERROR,
            errorMessage = message ?: "Unknown error",
        )
    }

    /** Clear the pose overlay and return to idle. */
    fun clearPose() {
        _uiState.value = _uiState.value.copy(
            status = Status.IDLE,
            sceneName = "",
            poseName = "",
            keypoints = emptyList(),
            errorMessage = null,
        )
    }

    /** Toggle between front and back camera. */
    fun toggleCamera() {
        _uiState.value = _uiState.value.copy(
            useFrontCamera = !_uiState.value.useFrontCamera,
        )
    }
}
