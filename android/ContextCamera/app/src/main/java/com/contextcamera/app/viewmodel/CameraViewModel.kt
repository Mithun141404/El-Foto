package com.contextcamera.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contextcamera.app.network.ApiClient
import com.contextcamera.app.network.Keypoint
import com.contextcamera.app.network.PoseRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the camera screen.
 */
data class CameraUiState(
    val status: Status = Status.IDLE,
    val scene: String = "",
    val poseName: String = "",
    val keypoints: List<Keypoint> = emptyList(),
    val errorMessage: String? = null,
    val useFrontCamera: Boolean = false,
)

enum class Status {
    IDLE,       // No pose overlay, camera running
    ANALYZING,  // Scene is being classified on-device
    LOADING,    // Waiting for backend to return pose
    POSE_READY, // Pose overlay is visible
    ERROR,      // Something went wrong
}

/**
 * ViewModel managing the camera screen state.
 */
class CameraViewModel : ViewModel() {

    companion object {
        private const val TAG = "CameraViewModel"
    }

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /**
     * Called when on-device scene classification completes.
     * Sends the scene tag to the backend to generate a pose.
     */
    fun onSceneDetected(scene: String) {
        _uiState.value = _uiState.value.copy(
            status = Status.LOADING,
            scene = scene,
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                Log.d(TAG, "Requesting pose for scene: $scene")
                val response = ApiClient.service.generatePose(PoseRequest(scene))

                Log.d(TAG, "Pose received: ${response.poseName} with ${response.keypoints.size} keypoints")

                _uiState.value = _uiState.value.copy(
                    status = Status.POSE_READY,
                    poseName = response.poseName,
                    keypoints = response.keypoints,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate pose", e)
                _uiState.value = _uiState.value.copy(
                    status = Status.ERROR,
                    errorMessage = "Failed to generate pose: ${e.message}",
                )
            }
        }
    }

    /**
     * Set status to ANALYZING while scene classification runs.
     */
    fun onAnalyzing() {
        _uiState.value = _uiState.value.copy(
            status = Status.ANALYZING,
            errorMessage = null,
        )
    }

    /**
     * Clear the pose overlay and return to idle.
     */
    fun clearPose() {
        _uiState.value = _uiState.value.copy(
            status = Status.IDLE,
            scene = "",
            poseName = "",
            keypoints = emptyList(),
            errorMessage = null,
        )
    }

    /**
     * Toggle between front and back camera.
     */
    fun toggleCamera() {
        _uiState.value = _uiState.value.copy(
            useFrontCamera = !_uiState.value.useFrontCamera
        )
    }
}
