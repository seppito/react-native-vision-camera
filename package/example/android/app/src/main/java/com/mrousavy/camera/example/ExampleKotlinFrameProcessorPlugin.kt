package com.mrousavy.camera.example

import android.util.Log
import android.media.Image.Plane

import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.frameprocessors.VisionCameraProxy
import android.opengl.GLES20
import java.nio.ByteBuffer
import android.graphics.ImageFormat



class ExampleKotlinFrameProcessorPlugin(proxy: VisionCameraProxy, options: Map<String, Any>?): FrameProcessorPlugin() {
    init {
        Log.d("ExampleKotlinPlugin", "ExampleKotlinFrameProcessorPlugin initialized with options: " + options?.toString())
    }

    companion object {
        private const val MAX_FRAMES = 100
        private val yTextureIds = IntArray(MAX_FRAMES)
        private val uTextureIds = IntArray(MAX_FRAMES)
        private val vTextureIds = IntArray(MAX_FRAMES)
        private var frameCount = 0
    }

    init {
        // Generate texture IDs for Y, U, and V planes
        GLES20.glGenTextures(MAX_FRAMES, yTextureIds, 0)
        GLES20.glGenTextures(MAX_FRAMES, uTextureIds, 0)
        GLES20.glGenTextures(MAX_FRAMES, vTextureIds, 0)
    }

    private fun checkGlError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("ExampleKotlinPlugin", "$operation: glError $error")
            throw RuntimeException("$operation: glError $error")
        }
    }

    override fun callback(frame: Frame, params: Map<String, Any>?): Any? {
        if (frameCount < MAX_FRAMES) {
            val image = frame.image

            if (image.format == ImageFormat.YUV_420_888) {
                val width = frame.width
                val height = frame.height

                // Store Y, U, V planes on GPU as separate textures
                uploadPlaneToTexture(image.planes[0], yTextureIds[frameCount], width, height, GLES20.GL_LUMINANCE)
                uploadPlaneToTexture(image.planes[1], uTextureIds[frameCount], width / 2, height / 2, GLES20.GL_LUMINANCE)
                uploadPlaneToTexture(image.planes[2], vTextureIds[frameCount], width / 2, height / 2, GLES20.GL_LUMINANCE)
                frameCount++
                Log.i("ExampleKotlinPlugin", "YUV frame stored on GPU. Total frames stored: $frameCount")
            } else {
                Log.e("ExampleKotlinPlugin", "Unsupported image format: ${image.format}")
            }
            if (frameCount == MAX_FRAMES) {
                Log.i("ExampleKotlinPlugin", "Max frame count reached. No more frames will be stored.")
            }
        }

        return hashMapOf(
            "status" to "YUV frame stored on GPU",
            "frameCount" to frameCount
        )
    }



    private fun uploadPlaneToTexture(plane: Plane , textureId: Int, width: Int, height: Int, format: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val buffer = plane.buffer
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            format,
            width,
            height,
            0,
            format,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )
        checkGlError("glTexImage2D")
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

    }

 
}
