package com.mrousavy.camera.react

import android.graphics.ImageFormat
import android.opengl.GLES20
import android.util.Log
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.LinkedList

class GLNativeManager private constructor() {

    companion object {
        const val TAG = "GLNativeManager"
        private const val MAX_FRAMES = 10
        private var debugMode = false

        // Singleton instance
        @Volatile
        private var instance: GLNativeManager? = null

        fun getInstance(): GLNativeManager {
            return instance ?: synchronized(this) {
                instance ?: GLNativeManager().also { instance = it }
            }
        }
    }

    private var glContextId: Int? = null
    private val frameStack = LinkedList<Int>()
    private val frameIds = mutableListOf<Int>()

    // Enable debug mode
    fun enableDebugMode(enable: Boolean) {
        debugMode = enable
        Log.d(TAG, "Debug mode set to $debugMode")
    }

    // Set the GL context ID
    fun setGLContextID(contextID: Int) {
        glContextId = contextID
        Log.d(TAG, "setGLContextID: glContextId set to $glContextId")
    }

    // Push frame to GPU and add texture ID to stack
    fun pushFrame(imageProxy: ImageProxy): Int {
        if (glContextId == null) {
            throw IllegalStateException("GL Context ID is not set")
        }

        val textureId = createTextureFromImageProxy(imageProxy)

        // Handle frame stack limits
        if (frameStack.size >= MAX_FRAMES) {
            val removedTextureId = frameStack.removeFirst()
            GLES20.glDeleteTextures(1, intArrayOf(removedTextureId), 0)
            frameIds.removeAt(0)
        }

        frameStack.addLast(textureId)
        frameIds.add(textureId)

        Log.d(TAG, "pushFrame: Pushed frame with texture ID $textureId")
        imageProxy.close()
        return textureId
    }

    // Create texture from ImageProxy
    private fun createTextureFromImageProxy(imageProxy: ImageProxy): Int {
        val textureIdArray = IntArray(1)
        GLES20.glGenTextures(1, textureIdArray, 0)
        val textureId = textureIdArray[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        Log.d(TAG, "Generated texture with ID: $textureId")

        // Set texture parameters
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        Log.d(TAG, "Texture parameters set: GL_LINEAR filtering")

        if (debugMode) {
            // Debug mode: Simulate frame creation with a single-color texture
            Log.d(TAG, "Debug mode enabled - creating single-color texture")
            val colorData = ByteArray(4) { if (it % 4 == 0) 255.toByte() else 0.toByte() } // RGBA: red
            val colorBuffer = ByteBuffer.wrap(colorData)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, colorBuffer
            )
            Log.d(TAG, "Single-color texture created in debug mode")
        } else {
            when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> {
                    Log.d(TAG, "Processing YUV_420_888 format")
                    // Handle YUV_420_888 format: Extract Y, U, and V planes to form an RGB texture
                    val yBuffer = imageProxy.planes[0].buffer
                    val uBuffer = imageProxy.planes[1].buffer
                    val vBuffer = imageProxy.planes[2].buffer

                    val yRowStride = imageProxy.planes[0].rowStride
                    val uRowStride = imageProxy.planes[1].rowStride
                    val vRowStride = imageProxy.planes[2].rowStride

                    val yPixelStride = imageProxy.planes[0].pixelStride
                    val uPixelStride = imageProxy.planes[1].pixelStride
                    val vPixelStride = imageProxy.planes[2].pixelStride

                    val width = imageProxy.width
                    val height = imageProxy.height
                    Log.d(TAG, "Image width: $width, height: $height")

                    // Convert YUV to RGB (simplified approach)
                    val rgbaBuffer = ByteBuffer.allocateDirect(width * height * 4) // RGBA

                    for (i in 0 until height) {
                        for (j in 0 until width) {
                            val yIndex = i * yRowStride + j * yPixelStride
                            val y = yBuffer.get(yIndex).toInt() and 0xFF

                            val uIndex = (i / 2) * uRowStride + (j / 2) * uPixelStride
                            val vIndex = (i / 2) * vRowStride + (j / 2) * vPixelStride

                            val u = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                            val v = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                            // Convert YUV to RGB
                            val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                            val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                            val b = (y + 1.772f * u).toInt().coerceIn(0, 255)

                            rgbaBuffer.put(r.toByte())
                            rgbaBuffer.put(g.toByte())
                            rgbaBuffer.put(b.toByte())
                            rgbaBuffer.put(0xFF.toByte()) // Alpha channel
                        }
                    }
                    rgbaBuffer.position(0)
                    Log.d(TAG, "YUV to RGB conversion complete, loading texture")

                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgbaBuffer
                    )
                    Log.d(TAG, "Texture created from YUV_420_888 image data")
                }

                ImageFormat.FLEX_RGBA_8888 -> {
                    Log.d(TAG, "Processing RGBA_8888 format")
                    // Handle RGBA format directly
                    val rgbaBuffer = imageProxy.planes[0].buffer
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        imageProxy.width, imageProxy.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgbaBuffer
                    )
                    Log.d(TAG, "Texture created from RGBA_8888 image data")
                }

                else -> {
                    Log.e(TAG, "Unsupported image format: ${imageProxy.format}")
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                    return -1
                }
            }
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind texture
        Log.d(TAG, "Texture creation complete and unbound, returning texture ID: $textureId")
        return textureId
    }

    // Retrieve the array of frame IDs
    fun getFrameIDs(): Array<Int> {
        return frameIds.toTypedArray()
    }
}
