import * as React from 'react';
import { StyleSheet, View, NativeModules } from 'react-native';
import { SAFE_AREA_PADDING } from './Constants';
import { GLView } from 'expo-gl';
import { PressableOpacity } from 'react-native-pressable-opacity';
import IonIcon from 'react-native-vector-icons/Ionicons';
import type { Routes } from './Routes';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

const { GLNativeModule } = NativeModules;

type Props = NativeStackScreenProps<Routes, 'OpenGLPage'>;

export function OpenGLPage({ navigation }: Props): React.ReactElement {
  async function onContextCreate(gl: any) {
    console.log("GL Context ID:", gl.contextId);

    // Flush the screen with white color first
    gl.clearColor(1.0, 1.0, 1.0, 1.0); // Set clear color to white (RGBA: 1, 1, 1, 1)
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.flush();
    gl.endFrameEXP();

    // Set the OpenGL context ID in the native module
    GLNativeModule.setGLContextID(gl.contextId);

    // Create a test texture on the native side
    await GLNativeModule.createTestTexture();

    let textureId = await GLNativeModule.getTestTextureID();
    console.log("Test Texture ID:", textureId);

    try {
      gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight);

      // Activate texture unit 1 and bind the native texture ID
      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, textureId);

      // Set texture parameters
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);

      // Render operations (modify as needed based on your rendering logic)
      gl.clear(gl.COLOR_BUFFER_BIT);

      // Ensure all drawing commands are flushed and rendered
      gl.flush();
      gl.endFrameEXP();
    } catch (error) {
      console.error("Failed to retrieve texture ID:", error);
    }
  }

  return (
    <View style={styles.container}>
      <GLView style={{ flex: 1 }} onContextCreate={onContextCreate} />

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
