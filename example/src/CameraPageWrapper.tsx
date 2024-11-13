import React, { useEffect, useState, useCallback } from 'react';
import { View, NativeModules } from 'react-native';
import { GLView } from 'expo-gl';
import { CameraPage } from './CameraPage';
import type { Routes } from './Routes';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type Props = NativeStackScreenProps<Routes, 'CameraPageWrapper'>;
const { GLNativeModule } = NativeModules;

export function CameraPageWrapper({ navigation }: Props): React.ReactElement {
  const [showCamera, setShowCamera] = useState<boolean>(true);

  const onContextCreate = useCallback(async (gl: any) => {
    console.log('GL Context ID:', gl.contextId);
    GLNativeModule.enableDebugMode(true);

    // Set up shaders
    const vertexShaderSource = `
        attribute vec2 a_position;
        attribute vec2 a_texCoord;
        varying vec2 v_texCoord;
        void main() {
            gl_Position = vec4(a_position, 0, 1);
            v_texCoord = a_texCoord;
        }
    `;

    const fragmentShaderSource = `
        precision mediump float;
        varying vec2 v_texCoord;
        uniform sampler2D u_texture;
        void main() {
            gl_FragColor = texture2D(u_texture, v_texCoord);
        }
    `;

    // Compile shaders
    const vertexShader = gl.createShader(gl.VERTEX_SHADER);
    gl.shaderSource(vertexShader, vertexShaderSource);
    gl.compileShader(vertexShader);
    if (!gl.getShaderParameter(vertexShader, gl.COMPILE_STATUS)) {
        console.error('Vertex shader compilation failed:', gl.getShaderInfoLog(vertexShader));
        return;
    }

    const fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
    gl.shaderSource(fragmentShader, fragmentShaderSource);
    gl.compileShader(fragmentShader);
    if (!gl.getShaderParameter(fragmentShader, gl.COMPILE_STATUS)) {
        console.error('Fragment shader compilation failed:', gl.getShaderInfoLog(fragmentShader));
        return;
    }

    // Create program
    const program = gl.createProgram();
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
        console.error('Program linking failed:', gl.getProgramInfoLog(program));
        return;
    }

    // Create buffer
    const positions = new Float32Array([
        -1, -1, 0, 0,
        1, -1, 1, 0,
        -1, 1, 0, 1,
        1, 1, 1, 1,
    ]);

    const positionBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, positions, gl.STATIC_DRAW);

    const render = async () => {
      const textureId = await GLNativeModule.createTestTexture();
      console.log('textureID', textureId);
      try {
        gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight);
        gl.clearColor(0, 0, 0, 1); // Setting clear color to black for testing
        gl.clear(gl.COLOR_BUFFER_BIT);

        // Use the texture ID directly
        const texture = textureId;

        // Use the program
        gl.useProgram(program);

        // Bind the position buffer
        gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);

        const positionLocation = gl.getAttribLocation(program, 'a_position');
        const texCoordLocation = gl.getAttribLocation(program, 'a_texCoord');

        // Enable attributes
        gl.enableVertexAttribArray(positionLocation);
        gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 16, 0);

        gl.enableVertexAttribArray(texCoordLocation);
        gl.vertexAttribPointer(texCoordLocation, 2, gl.FLOAT, false, 16, 8);

        // Bind the texture
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, texture);

        // Set the sampler uniform
        const samplerLocation = gl.getUniformLocation(program, 'u_texture');
        gl.uniform1i(samplerLocation, 0);

        // Draw the quad
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

        gl.flush();
        gl.endFrameEXP();

        // Schedule the next frame
        requestAnimationFrame(render);
      } catch (error) {
        console.error("Failed to retrieve texture ID:", error);
      }
    };

    // Trigger the render function after 6 seconds
    setTimeout(() => {
      render();
    }, 6000);

  }, []);

  return (
    <View style={{ flex: 1 }}>
      <GLView style={{ flex: 1 }} onContextCreate={onContextCreate} />
      {showCamera && (
        <CameraPage navigation={navigation} />
      )}
    </View>
  );
}
