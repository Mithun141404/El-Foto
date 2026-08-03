package com.contextcamera.app.network

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit interface for the Context Camera backend.
 *
 * Sends a raw JPEG camera frame to the backend, where Gemini Vision
 * analyzes the actual scene and generates a matching 33-point pose.
 */
interface ApiService {

    @Multipart
    @POST("/analyze-and-pose")
    suspend fun analyzeAndPose(@Part image: MultipartBody.Part): PoseResponse
}
