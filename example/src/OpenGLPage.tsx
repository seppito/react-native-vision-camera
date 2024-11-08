import * as React from 'react';
import { StyleSheet, View, NativeModules } from 'react-native';
import { SAFE_AREA_PADDING } from './Constants'

import { GLView } from 'expo-gl';
import { PressableOpacity } from 'react-native-pressable-opacity';
import IonIcon from 'react-native-vector-icons/Ionicons';
import type { Routes } from './Routes';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

const { GLNativeManager } = NativeModules;

type Props = NativeStackScreenProps<Routes, 'OpenGLPage'>;

export function OpenGLPage({ navigation }: Props): React.ReactElement {
  async function onContextCreate(gl: any) {
    console.log("GL Context ID:", gl.contextId);

    // Set the OpenGL context ID in the native module
    GLNativeManager.setGLContextID(gl.contextId);

    // Create a test texture on the native side
    await GLNativeManager.createTestTexture();
    
    // Retrieve the texture ID from the native side
    const textureId = await GLNativeManager.getTestTextureID();
    console.log("Test Texture ID:", textureId);

    gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight);
    gl.clearColor(0, 1, 1, 1);

    // Create shaders and draw a shape using the test texture
    const vert = gl.createShader(gl.VERTEX_SHADER);
    gl.shaderSource(vert, `
      attribute vec2 position;
      varying vec2 uv;
      void main() {
        gl_Position = vec4(position, 0, 1);
        uv = position * 0.5 + 0.5;
      }
    `);
    gl.compileShader(vert);

    const frag = gl.createShader(gl.FRAGMENT_SHADER);
    gl.shaderSource(frag, `
      precision mediump float;
      varying vec2 uv;
      uniform sampler2D texture;
      void main() {
        gl_FragColor = texture2D(texture, uv);
      }
    `);
    gl.compileShader(frag);

    const program = gl.createProgram();
    gl.attachShader(program, vert);
    gl.attachShader(program, frag);
    gl.linkProgram(program);
    gl.useProgram(program);

    const position = gl.getAttribLocation(program, 'position');
    gl.enableVertexAttribArray(position);
    const buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);

    const texture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);

    // Use the test texture ID from native code
    gl.bindTexture(gl.TEXTURE_2D, textureId);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

    gl.flush();
    gl.endFrameEXP();
  }

  return (
    <View style={styles.container}>
      <GLView style={{ flex: 1 }} onContextCreate={onContextCreate} />

      {/* Back Button */}
      <PressableOpacity style={styles.backButton} onPress={navigation.goBack}>
        <IonIcon name="chevron-back" color="white" size={35} />
      </PressableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  backButton: {
    position: 'absolute',
    left: SAFE_AREA_PADDING.paddingLeft,
    top: SAFE_AREA_PADDING.paddingTop,
  },
});
