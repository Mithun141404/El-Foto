package com.contextcamera.app.network

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the Context Camera backend.
 */
interface ApiService {

    @POST("/generate-pose")
    suspend fun generatePose(@Body request: PoseRequest): PoseResponse
}
