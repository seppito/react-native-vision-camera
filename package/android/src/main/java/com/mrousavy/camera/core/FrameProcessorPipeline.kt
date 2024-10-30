package com.mrousavy.camera.core
import android.graphics.ImageFormat

import androidx.annotation.OptIn
import android.opengl.GLES20
import android.util.Log
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ExperimentalGetImage
import java.nio.ByteBuffer
import androidx.camera.core.ImageProxy
import com.mrousavy.camera.frameprocessors.Frame

class FrameProcessorPipeline(private val callback: CameraSession.Callback) : Analyzer {

    companion object {
        private const val MAX_FRAMES = 100
        private val fboIds = IntArray(MAX_FRAMES)
        private val textureIds = IntArray(MAX_FRAMES)
        private var frameCount = 0
    }

    init {
        GLES20.glGenFramebuffers(MAX_FRAMES, fboIds, 0)
        GLES20.glGenTextures(MAX_FRAMES, textureIds, 0)
    }

    private fun checkGlError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("FrameProcessorPipeline", "$operation: glError $error")
            throw RuntimeException("$operation: glError $error")
        }
    }

@OptIn(ExperimentalGetImage::class)
override fun analyze(imageProxy: ImageProxy) {
    val frame = Frame(imageProxy)

    try {
        frame.incrementRefCount()

        if (frameCount < MAX_FRAMES) {
            Log.i("FrameProcessorPipeline", "Frame storing process in progress...")

            val fboId = fboIds[frameCount]
            val textureId = textureIds[frameCount]

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            checkGlError("glBindFramebuffer")

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            checkGlError("glBindTexture")

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            checkGlError("glTexParameteri")

            // Allocate texture storage
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                frame.width,
                frame.height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
            )
            checkGlError("glTexImage2D")

            // Get pixel buffer from frame's ImageProxy
            val buffer = extractPixelBuffer(frame)

            // Upload pixel buffer data to the texture
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                frame.width,
                frame.height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer
            )
            checkGlError("glTexSubImage2D")

            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textureId,
                0
            )
            Log.i("FrameProcessorPipeline", "$textureId")
            checkGlError("glFramebufferTexture2D")
            /*
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e("FrameProcessorPipeline", "Framebuffer not complete: Status $status")
                throw RuntimeException("Framebuffer is not complete: $status")
            } else {
                Log.i("FrameProcessorPipeline", "Framebuffer is complete.")
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            checkGlError("glClear")

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            checkGlError("glBindFramebuffer (unbind)")
            
             */
            

            frameCount++
        } else {
            Log.i("FrameProcessorPipeline", "Max frame count reached. No more frames will be stored.")
            return
        }

        callback.onFrame(frame)
    } finally {
        frame.decrementRefCount()
        imageProxy.close() // Close the image to free resources
    }
}

private fun extractPixelBuffer(frame: Frame): ByteBuffer {
    val image = frame.getImage()
    val width = frame.width
    val height = frame.height

    // Prepare an RGBA buffer to hold the pixel data
    val rgbaBuffer = ByteBuffer.allocateDirect(width * height * 4) // 4 bytes per pixel for RGBA

    // Check if the image format is YUV (adjust as needed based on actual format)
    if (image.format == ImageFormat.YUV_420_888) {
        // For YUV format, we need to convert YUV to RGBA. We process each plane individually.
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // YUV to RGBA conversion
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = y * yRowStride + x
                val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                val yValue = yBuffer.get(yIndex).toInt() and 0xFF
                val uValue = uBuffer.get(uvIndex).toInt() and 0xFF
                val vValue = vBuffer.get(uvIndex).toInt() and 0xFF

                // Convert YUV to RGB
                val r = (yValue + (1.370705 * (vValue - 128))).toInt().coerceIn(0, 255)
                val g = (yValue - (0.337633 * (uValue - 128)) - (0.698001 * (vValue - 128))).toInt().coerceIn(0, 255)
                val b = (yValue + (1.732446 * (uValue - 128))).toInt().coerceIn(0, 255)

                // Write RGBA values to the buffer
                rgbaBuffer.put((r and 0xFF).toByte())
                rgbaBuffer.put((g and 0xFF).toByte())
                rgbaBuffer.put((b and 0xFF).toByte())
                rgbaBuffer.put(0xFF.toByte()) // Alpha channel (opaque)
            }
        }
    } else if (image.format == ImageFormat.JPEG || image.format == android.graphics.PixelFormat.RGBA_8888) {
        // If the format is already compatible, just copy it directly     
        val plane = image.planes[0]
        val buffer = plane.buffer
        rgbaBuffer.put(buffer)
    } else {
        throw UnsupportedOperationException("Image format not supported")
    }

    rgbaBuffer.rewind()
    return rgbaBuffer
}

}
