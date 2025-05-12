/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.objectdetection

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ar.sceneform.rendering.ModelRenderable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * Helper class to manage 3D models for augmented reality.
 * This class handles loading and caching glTF models for use with ARCore/Sceneform.
 */
class ModelManager(private val context: Context) {

    private val modelCache = mutableMapOf<String, ModelRenderable>()
    private val loadingModels = mutableMapOf<String, CompletableFuture<ModelRenderable>>()

    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIRECTORY = "models"

        // Map category labels from MediaPipe object detection to model filenames
        val CATEGORY_TO_MODEL_MAP = mapOf(
            "chair" to "chair.glb",
            "couch" to "sofa.glb",
            "dining table" to "table.glb",
            "tv" to "television.glb",
            // Add more mappings as needed
            "default" to "chair.glb" // Default model if no match is found
        )
    }

    /**
     * Gets a model renderable for the given category label.
     * @param category The object category from MediaPipe detection
     * @return A CompletableFuture that will resolve to the ModelRenderable
     */
    fun getModelForCategory(category: String): CompletableFuture<ModelRenderable> {
        val modelFile = CATEGORY_TO_MODEL_MAP[category] ?: CATEGORY_TO_MODEL_MAP["default"]!!
        return getModel(modelFile)
    }

    /**
     * Gets a model renderable for the given filename.
     * @param filename The filename of the model in the assets/models directory
     * @return A CompletableFuture that will resolve to the ModelRenderable
     */
    fun getModel(filename: String): CompletableFuture<ModelRenderable> {
        // Check if model is already cached
        modelCache[filename]?.let {
            return CompletableFuture.completedFuture(it)
        }

        // Check if model is already loading
        loadingModels[filename]?.let {
            return it
        }

        // Otherwise, load model
        val modelUri = Uri.parse("file:///android_asset/${MODELS_DIRECTORY}/${filename}")
        val future = ModelRenderable.builder()
            .setSource(context, modelUri)
            .setIsFilamentGltf(true)
            .build()
            .thenApply { modelRenderable: ModelRenderable ->
                modelCache[filename] = modelRenderable
                loadingModels.remove(filename)
                modelRenderable
            }
            .exceptionally { throwable: Throwable? ->
                Log.e(TAG, "Unable to load model: $filename", throwable)
                loadingModels.remove(filename)
                null
            }

        loadingModels[filename] = future
        return future
    }

    /**
     * Clears all cached models and cancels any pending loads.
     */
    fun clearCache() {
        modelCache.clear()
        loadingModels.clear()
    }

    /**
     * Disposes of all model renderables in the cache.
     * Should be called when the application is destroyed.
     */
    fun dispose() {
        modelCache.values.forEach { model ->
            model.filamentAsset?.let { asset ->
                asset.releaseSourceData()
            }
        }
        clearCache()
    }
}