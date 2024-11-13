package com.mrousavy.camera.react

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import androidx.camera.core.ImageProxy

class GLNativeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val glManager = GLNativeManager.getInstance()

    override fun getName(): String = "GLNativeModule"

    @ReactMethod
    fun enableDebugMode(enable: Boolean) {
        glManager.enableDebugMode(enable)
    }

    @ReactMethod
    fun pushFrame(imageProxy: ImageProxy, promise: Promise) {
        try {
            val textureId = glManager.pushFrame(imageProxy)
            promise.resolve(textureId)
        } catch (e: Exception) {
            promise.reject("PUSH_FAILED", "Failed to push frame", e)
        }
    }

    @ReactMethod
    fun getFrameIDs(promise: Promise) {
        promise.resolve(glManager.getFrameIDs())
    }

    @ReactMethod
    fun createTestTexture(promise: Promise) {
        try {
            val textureId = glManager.createTestTexture()
            promise.resolve(textureId)
        } catch (e: Exception) {
            promise.reject("CREATE_TEST_TEXTURE_FAILED", "Failed to create test texture", e)
        }
    }

    @ReactMethod
    fun getTestTextureID(promise: Promise) {
        val textureId = glManager.getTestTextureID()
        if (textureId != null) {
            promise.resolve(textureId)
        } else {
            promise.reject("NO_TEST_TEXTURE", "No test texture ID available")
        }
    }
}
