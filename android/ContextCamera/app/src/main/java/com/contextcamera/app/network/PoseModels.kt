package com.contextcamera.app.network

import com.google.gson.annotations.SerializedName

/**
 * Request body for the /generate-pose endpoint.
 */
data class PoseRequest(
    @SerializedName("scene") val scene: String
)

/**
 * A single keypoint with normalized x/y coordinates (0.0 to 1.0).
 */
data class Keypoint(
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float
)

/**
 * Response from the /generate-pose endpoint.
 */
data class PoseResponse(
    @SerializedName("scene") val scene: String,
    @SerializedName("pose_name") val poseName: String,
    @SerializedName("keypoints") val keypoints: List<Keypoint>
)
