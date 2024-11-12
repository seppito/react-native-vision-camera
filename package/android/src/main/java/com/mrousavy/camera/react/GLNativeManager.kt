package com.mrousavy.camera.react

import android.graphics.ImageFormat
import android.opengl.GLES20
import android.util.Log
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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

    // Create texture from ImageProxy using shaders for YUV to RGB conversion
    private fun createTextureFromImageProxy(imageProxy: ImageProxy): Int {
        if (debugMode) {
            // Debug mode: Simulate frame creation with a single-color texture
            Log.d(TAG, "Debug mode enabled - creating single-color texture")
            val textureIdArray = IntArray(1)
            GLES20.glGenTextures(1, textureIdArray, 0)
            val textureId = textureIdArray[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            val colorData = ByteArray(4) { if (it % 4 == 0) 255.toByte() else 0.toByte() } // RGBA: red
            val colorBuffer = ByteBuffer.wrap(colorData)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, colorBuffer
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            Log.d(TAG, "Single-color texture created in debug mode")
            return textureId
        }

        if (imageProxy.format != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Unsupported image format: ${imageProxy.format}")
            return -1
        }

        // Generate texture IDs for Y, U, and V planes
        val textureIds = IntArray(3)
        GLES20.glGenTextures(3, textureIds, 0)
        val yTextureId = textureIds[0]
        val uTextureId = textureIds[1]
        val vTextureId = textureIds[2]

        uploadPlaneToTexture(yTextureId, imageProxy.planes[0])
        uploadPlaneToTexture(uTextureId, imageProxy.planes[1])
        uploadPlaneToTexture(vTextureId, imageProxy.planes[2])

        // Create a framebuffer object (FBO) to render the YUV data into an RGB texture
        val rgbTextureId = renderYUVToRGBTexture(yTextureId, uTextureId, vTextureId, imageProxy.width, imageProxy.height)

        // Clean up YUV textures after rendering
        GLES20.glDeleteTextures(3, textureIds, 0)

        return rgbTextureId
    }

    // Upload plane data to texture
    private fun uploadPlaneToTexture(textureId: Int, plane: ImageProxy.PlaneProxy) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val buffer = plane.buffer
        buffer.position(0)

        // For Y plane, pixelStride is usually 1 and for U/V planes, it can be 2
        val width = plane.rowStride / plane.pixelStride
        val height = plane.buffer.remaining() / plane.rowStride

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
            width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer
        )

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    // Render YUV textures to an RGB texture using shaders
    private fun renderYUVToRGBTexture(yTextureId: Int, uTextureId: Int, vTextureId: Int, width: Int, height: Int): Int {
        // Generate RGB texture
        val rgbTextureIdArray = IntArray(1)
        GLES20.glGenTextures(1, rgbTextureIdArray, 0)
        val rgbTextureId = rgbTextureIdArray[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rgbTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )

        // Create and bind FBO
        val frameBuffer = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBuffer, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, rgbTextureId, 0
        )

        // Set up viewport
        GLES20.glViewport(0, 0, width, height)

        // Use shader program
        val program = createYUVtoRGBProgram()
        GLES20.glUseProgram(program)

        // Set up vertex data
        val vertexBuffer = createFullScreenQuad()
        val positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // Bind YUV textures
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_TextureY"), 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_TextureU"), 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, vTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_TextureV"), 2)

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, frameBuffer, 0)
        GLES20.glDeleteProgram(program)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return rgbTextureId
    }

    // Create shader program for YUV to RGB conversion
    private fun createYUVtoRGBProgram(): Int {
        val vertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D u_TextureY;
            uniform sampler2D u_TextureU;
            uniform sampler2D u_TextureV;
            const mat3 yuv2rgb = mat3(
                1.0,     1.0,     1.0,
                0.0,    -0.39465, 2.03211,
                1.13983,-0.58060, 0.0
            );
            void main() {
                float y = texture2D(u_TextureY, v_TexCoord).r;
                float u = texture2D(u_TextureU, v_TexCoord).r - 0.5;
                float v = texture2D(u_TextureV, v_TexCoord).r - 0.5;
                vec3 rgb = yuv2rgb * vec3(y, u, v);
                gl_FragColor = vec4(rgb, 1.0);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Error creating program.")
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        // Check for linking errors
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking program: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    // Helper method to compile shaders
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type $type.")
            return 0
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Check for compilation errors
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    // Create a full-screen quad for rendering
    private fun createFullScreenQuad(): FloatBuffer {
        val vertices = floatArrayOf(
            // Positions    // Texture Coords
            -1f, -1f,      0f, 0f,
            1f, -1f,       1f, 0f,
            -1f, 1f,       0f, 1f,
            1f, 1f,        1f, 1f
        )
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertices)
        buffer.position(0)
        return buffer
    }

    // Retrieve the array of frame IDs
    fun getFrameIDs(): Array<Int> {
        return frameIds.toTypedArray()
    }
}
