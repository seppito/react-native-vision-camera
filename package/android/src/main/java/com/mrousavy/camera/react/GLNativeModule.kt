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
    fun setGLContextID(contextID: Int) {
        glManager.setGLContextID(contextID)
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
}
