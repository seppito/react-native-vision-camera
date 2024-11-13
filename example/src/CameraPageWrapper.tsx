// CameraPageWrapper.tsx

import React, { useEffect, useState, useCallback } from 'react';
import { View,NativeModules } from 'react-native';
import { GLView } from 'expo-gl';
import { CameraPage } from './CameraPage';
import type { Routes } from './Routes';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type Props = NativeStackScreenProps<Routes, 'CameraPageWrapper'>;
const { GLNativeModule } = NativeModules;

export function CameraPageWrapper({ navigation }: Props): React.ReactElement {
  const [glContext, setGlContext] = useState<any>(null);
  const [showCamera, setShowCamera] = useState<boolean>(true);

  useEffect(() => {
    // Hide the camera after 20 seconds
    const timer = setTimeout(() => {
      setShowCamera(false);
      // If you want to navigate to OpenGLPage after hiding the camera, uncomment the next line
      // navigation.navigate('OpenGLPage');
    }, 20000); // 20000 milliseconds = 20 seconds

    return () => clearTimeout(timer);
  }, [navigation]);

  const onContextCreate = useCallback((gl: any) => {
    console.log('Setting GL Context ID in native module:', gl.contextId);
    // Assuming GLNativeModule is imported and has setGLContextID method
    GLNativeModule.setGLContextID(gl.contextId);
    setGlContext(gl);
    // Set the OpenGL context ID in the native module
  }, []);

  return (
    <View style={{ flex: 1 }}>
      {/* Create the OpenGL context */}
      <GLView style={{ flex: 1 }} onContextCreate={onContextCreate} />

      {/* Conditionally render the CameraPage */}
      {showCamera && glContext && (
        <CameraPage gl={glContext} navigation={navigation} />
      )}
    </View>
  );
}
