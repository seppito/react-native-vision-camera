package com.mrousavy.camera.react

import android.graphics.ImageFormat
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES20
import android.util.Log
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.LinkedList
import android.graphics.PixelFormat

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

    private val frameStack = LinkedList<Int>()
    private val frameIds = mutableListOf<Int>()
    private val sharedFrameIds = mutableListOf<Int>()
    private var testTextureId: Int? = null

    // Enable debug mode
    fun enableDebugMode(enable: Boolean) {
        debugMode = enable
        Log.d(TAG, "Debug mode set to $debugMode")
    }

    // Check if EGL context is valid
    private fun getCurrentEGLContext(): EGLContext? {
        return EGL14.eglGetCurrentContext().takeIf { it != EGL14.EGL_NO_CONTEXT }
    }

    fun pushFrame(imageProxy: ImageProxy): Int {
        val eglContext = getCurrentEGLContext()
        if (eglContext == null) {
            Log.d(TAG, "No valid EGL context is available")
            return -1
        }
    
        val textureId = createTextureFromImageProxy(imageProxy)
    
        if (textureId == -1) {
            Log.e(TAG, "Failed to create texture from ImageProxy")
            return -1
        }
    
        // Handle frame stack limits
        if (frameStack.size >= MAX_FRAMES) {
            val removedTextureId = frameStack.removeFirst()
            frameIds.removeAt(0)
            // Delete the oldest texture to free up resources
            GLES20.glDeleteTextures(1, intArrayOf(removedTextureId), 0)
        }
    
        frameStack.addLast(textureId)
        frameIds.add(textureId)
    
        Log.d(TAG, "pushFrame: Pushed frame with texture ID $textureId")
        return textureId
    }
    

    // Create texture from ImageProxy supporting YUV and RGBA formats
    private fun createTextureFromImageProxy(imageProxy: ImageProxy): Int {
        when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                // Handle YUV format
                Log.d(TAG, "Processing YUV_420_888 format")
                // Implement YUV to RGB conversion if needed
                // For simplicity, we'll return -1 here
                return -1
            }

            ImageFormat.FLEX_RGBA_8888, PixelFormat.RGBA_8888 -> {
                // Handle RGBA format
                Log.d(TAG, "Processing RGBA format")
                val textureIdArray = IntArray(1)
                GLES20.glGenTextures(1, textureIdArray, 0)
                val textureId = textureIdArray[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

                // Set texture parameters
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

                val buffer = imageProxy.planes[0].buffer
                buffer.position(0)

                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    imageProxy.width, imageProxy.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                )

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                Log.d(TAG, "Texture created from RGBA image data with texture ID $textureId")
                return textureId
            }

            else -> {
                Log.e(TAG, "Unsupported image format: ${imageProxy.format}")
                return -1
            }
        }
    }

    // Retrieve the array of frame IDs
    fun getFrameIDs(): Array<Int> {
        return frameIds.toTypedArray()
    }

    // Create test texture
    fun createTestTexture(): Int {
        val eglContext = getCurrentEGLContext() ?: throw IllegalStateException("No valid EGL context is available")

        // Simulate frame creation with a single-color texture
        Log.d(TAG, "Creating test texture")
        val textureIdArray = IntArray(1)
        GLES20.glGenTextures(1, textureIdArray, 0)
        val textureId = textureIdArray[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val colorData = ByteArray(4) { if (it == 0) 255.toByte() else 0.toByte() } // RGBA: red
        val colorBuffer = ByteBuffer.wrap(colorData)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, colorBuffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        Log.d(TAG, "Test texture created with ID $textureId")
        testTextureId = textureId
        return textureId
    }

    // Get test texture ID
    fun getTestTextureID(): Int? {
        return testTextureId
    }
}
