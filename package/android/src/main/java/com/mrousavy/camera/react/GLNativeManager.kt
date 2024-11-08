// MyGLModule.kt
package com.mrousavy.camera.react

import android.opengl.GLES20
import android.util.Log // Import Log for logging
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.nio.ByteBuffer

class GLNativeManager(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val TAG = "GLNativeManager"
        private var glContextId: Int? = null
        private var testTextureId: Int? = null
    }

    override fun getName(): String = TAG

    @ReactMethod
    fun setGLContextID(contextID: Int) {
        glContextId = contextID
        Log.d(TAG, "setGLContextID: glContextId set to $glContextId")
    }

    @ReactMethod
    fun createTestTexture() {
        Log.d(TAG, "createTestTexture: Starting texture creation")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        Log.d(TAG, "createTestTexture: Generated texture with ID $textureId")

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        Log.d(TAG, "createTestTexture: Set texture parameters")

        // Define a simple color for the texture (red in this case)
        val colorData = ByteArray(4 * 1 * 1) { if (it % 4 == 0) 255.toByte() else 0.toByte() }  // RGBA: red
        val colorBuffer = ByteBuffer.wrap(colorData)
        Log.d(TAG, "createTestTexture: Prepared color data buffer")

        // Upload color data to the texture
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, colorBuffer
        )
        Log.d(TAG, "createTestTexture: Uploaded color data to texture")

        testTextureId = textureId
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind texture
        Log.d(TAG, "createTestTexture: Texture creation complete and texture unbound")
    }

    @ReactMethod
    fun getTestTextureID(promise: Promise) {
        if (testTextureId != null) {
            Log.d(TAG, "getTestTextureID: Returning texture ID $testTextureId")
            promise.resolve(testTextureId)
        } else {
            Log.d(TAG, "getTestTextureID: Texture ID not available")
            promise.reject("NO_TEXTURE_ID", "Test texture ID not available")
        }
    }
}
