package com.contextcamera.app.network

import com.google.gson.annotations.SerializedName

/**
 * A single keypoint with normalized x/y coordinates (0.0 to 1.0).
 */
data class Keypoint(
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float,
)

/**
 * Response from the /analyze-and-pose endpoint.
 *
 * [sceneName] is now a vivid, AI-generated description of the actual
 * scene visible in the camera (e.g. "sun-drenched golden beach at sunset"),
 * not a hardcoded category label.
 *
 * [keypoints] contains exactly 33 BlazePose landmarks.
 */
data class PoseResponse(
    @SerializedName("scene_name") val sceneName: String,
    @SerializedName("pose_name") val poseName: String,
    @SerializedName("keypoints") val keypoints: List<Keypoint>,
)
