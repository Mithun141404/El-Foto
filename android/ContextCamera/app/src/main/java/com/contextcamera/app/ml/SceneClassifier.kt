package com.contextcamera.app.ml

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device scene classifier using ML Kit Image Labeling.
 * Maps detected labels to high-level scene categories.
 */
class SceneClassifier {

    companion object {
        private const val TAG = "SceneClassifier"

        // Map of ML Kit labels → our scene categories
        private val SCENE_MAPPINGS = mapOf(
            // Graduation
            "graduation" to "Graduation", "mortarboard" to "Graduation",
            "academic" to "Graduation", "diploma" to "Graduation",
            "cap" to "Graduation", "gown" to "Graduation",

            // Cafe / Restaurant
            "coffee" to "Cafe", "cafe" to "Cafe", "restaurant" to "Cafe",
            "food" to "Cafe", "table" to "Cafe", "cup" to "Cafe",
            "drink" to "Cafe", "meal" to "Cafe", "dining" to "Cafe",

            // Beach
            "beach" to "Beach", "sand" to "Beach", "ocean" to "Beach",
            "sea" to "Beach", "wave" to "Beach", "coast" to "Beach",
            "shore" to "Beach", "surfing" to "Beach",

            // Gym / Sports
            "gym" to "Gym", "fitness" to "Gym", "exercise" to "Gym",
            "weight" to "Gym", "sport" to "Gym", "workout" to "Gym",
            "dumbbell" to "Gym", "basketball" to "Gym",

            // Nature
            "tree" to "Nature", "forest" to "Nature", "mountain" to "Nature",
            "flower" to "Nature", "garden" to "Nature", "park" to "Nature",
            "grass" to "Nature", "lake" to "Nature", "river" to "Nature",
            "waterfall" to "Nature", "plant" to "Nature", "leaf" to "Nature",
            "sunset" to "Nature", "sunrise" to "Nature",

            // Urban
            "building" to "Urban", "city" to "Urban", "street" to "Urban",
            "car" to "Urban", "road" to "Urban", "skyscraper" to "Urban",
            "bridge" to "Urban", "architecture" to "Urban",
            "traffic" to "Urban", "downtown" to "Urban",

            // Indoor
            "room" to "Indoor", "furniture" to "Indoor", "sofa" to "Indoor",
            "bed" to "Indoor", "chair" to "Indoor", "couch" to "Indoor",
            "living room" to "Indoor", "bedroom" to "Indoor",
            "office" to "Indoor", "desk" to "Indoor",
        )
    }

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    /**
     * Classify scene from a CameraX ImageProxy.
     * Returns a scene category string like "Cafe", "Beach", etc.
     */
    @androidx.camera.core.ExperimentalGetImage
    suspend fun classifyScene(imageProxy: ImageProxy): String {
        return suspendCancellableCoroutine { continuation ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                continuation.resume("Outdoor")
                return@suspendCancellableCoroutine
            }

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            labeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    imageProxy.close()

                    var bestScene = "Outdoor"  // default fallback
                    var bestConfidence = 0f

                    for (label in labels) {
                        val labelText = label.text.lowercase()
                        Log.d(TAG, "Label: ${label.text} (${label.confidence})")

                        for ((keyword, scene) in SCENE_MAPPINGS) {
                            if (labelText.contains(keyword) && label.confidence > bestConfidence) {
                                bestScene = scene
                                bestConfidence = label.confidence
                            }
                        }
                    }

                    Log.d(TAG, "Classified scene: $bestScene (confidence: $bestConfidence)")
                    continuation.resume(bestScene)
                }
                .addOnFailureListener { e ->
                    imageProxy.close()
                    Log.e(TAG, "Scene classification failed", e)
                    continuation.resume("Outdoor")
                }
        }
    }
}
