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
import android.util.Log
import android.view.MotionEvent
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.concurrent.CompletableFuture

/**
 * Helper class to integrate ARCore with MediaPipe object detection.
 * This class handles AR scene management and overlaying 3D models on detected objects.
 */
class ARCoreHelper(
    private val context: Context,
    private val arFragment: ArFragment,
    private val modelManager: ModelManager
) {
    private val anchors = mutableMapOf<String, AnchorNode>()
    private val scene: Scene get() = arFragment.arSceneView.scene

    companion object {
        private const val TAG = "ARCoreHelper"

        // Scale factors for different object categories
        private val CATEGORY_SCALE_FACTORS = mapOf(
            "chair" to Vector3(0.5f, 0.5f, 0.5f),
            "couch" to Vector3(0.7f, 0.7f, 0.7f),
            "dining table" to Vector3(0.8f, 0.8f, 0.8f),
            "tv" to Vector3(0.6f, 0.6f, 0.6f),
            // Default scale factor
            "default" to Vector3(0.5f, 0.5f, 0.5f)
        )
    }

    init {
        setupPlaneListener()
    }

    /**
     * Setup listener for plane taps in AR.
     */
    private fun setupPlaneListener() {
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _: MotionEvent ->
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                return@setOnTapArPlaneListener
            }

            // Create anchor at tap position
            val anchor = hitResult.createAnchor()

            // Load a default model for manually placed anchors
            modelManager.getModel("chair.glb").thenAccept { model ->
                placeModelAtAnchor(model, anchor, "chair", "user_placed_" + System.currentTimeMillis())
            }
        }
    }

    /**
     * Places a 3D model on a detected object based on MediaPipe detection results.
     * @param detectionResult The object detection result from MediaPipe
     * @param viewWidth Width of the view containing the detection
     * @param viewHeight Height of the view containing the detection
     */
    fun placeModelOnDetectedObject(
        detectionResult: ObjectDetectorResult,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (detectionResult.detections().isEmpty()) return

        val frame = arFragment.arSceneView.arFrame ?: return

        // Process each detection
        detectionResult.detections().forEach { detection ->
            // Get the center of the bounding box in screen coordinates
            val boundingBox = detection.boundingBox()
            val centerX = boundingBox.centerX() / viewWidth.toFloat()
            val centerY = boundingBox.centerY() / viewHeight.toFloat()

            // Try to get a hit result at this position
            val hits = frame.hitTest(centerX, centerY)
            if (hits.isEmpty()) return@forEach

            // Find the first hit on a plane
            val hitResult = hits.firstOrNull { hit ->
                hit.trackable is Plane && (hit.trackable as Plane).isPoseInPolygon(hit.hitPose)
            } ?: return@forEach

            // Get the detected object category
            val category = detection.categories()[0]
            val categoryName = category.categoryName()
            val detectionId = categoryName + "_" + System.currentTimeMillis()

            // Create an anchor at the hit position
            val anchor = hitResult.createAnchor()

            // Load the appropriate 3D model
            modelManager.getModelForCategory(categoryName).thenAccept { model ->
                placeModelAtAnchor(model, anchor, categoryName, detectionId)
            }
        }
    }

    /**
     * Places a 3D model at an AR anchor.
     * @param model The ModelRenderable to place
     * @param anchor The ARCore anchor to attach the model to
     * @param category The object category
     * @param anchorId A unique identifier for this anchor
     */
    private fun placeModelAtAnchor(
        model: ModelRenderable,
        anchor: Anchor,
        category: String,
        anchorId: String
    ) {
        // Remove existing anchor with the same ID
        anchors[anchorId]?.let {
            scene.removeChild(it)
            it.anchor?.detach()
            anchors.remove(anchorId)
        }

        // Create a new anchor node
        val anchorNode = AnchorNode(anchor).apply {
            setParent(scene)
        }

        // Create the transformable node for the model
        val modelNode = TransformableNode(arFragment.transformationSystem).apply {
            setParent(anchorNode)
            renderable = model

            // Scale the model based on category
            val scaleFactor = CATEGORY_SCALE_FACTORS[category] ?: CATEGORY_SCALE_FACTORS["default"]!!
            localScale = scaleFactor
        }

        // Store the anchor node for later reference
        anchors[anchorId] = anchorNode
    }

    /**
     * Removes all placed models and anchors.
     */
    fun clearAnchors() {
        anchors.values.forEach { anchorNode ->
            scene.removeChild(anchorNode)
            anchorNode.anchor?.detach()
        }
        anchors.clear()
    }

    /**
     * Clean up resources when the helper is no longer needed.
     */
    fun destroy() {
        clearAnchors()
    }
}