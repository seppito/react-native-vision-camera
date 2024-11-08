// MyGLModule.kt
package com.mrousavy.camera.react

import android.opengl.GLES20
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

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
    }

    // Method to generate a test texture with a solid color
    @ReactMethod
    fun createTestTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Define a simple color for the texture (red in this case)
        val colorData = ByteArray(4 * 1 * 1) { if (it % 4 == 0) 255.toByte() else 0.toByte() }  // RGBA: red
        val colorBuffer = java.nio.ByteBuffer.wrap(colorData)

        // Upload color data to the texture
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, colorBuffer
        )

        testTextureId = textureId
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind texture
    }

    // Method to retrieve the test texture ID for rendering in JavaScript
    @ReactMethod
    fun getTestTextureID(): Int? {
        return testTextureId
    }
}
 