// App.tsx — Moda Mobile 루트 네비게이션 (A안 에디토리얼 + 풀스크린 카메라)

import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import type { RootStackParamList } from './src/navigation/types';
import { BodyCaptureScreen } from './src/screens/BodyCaptureScreen';
import { ChatScreen } from './src/screens/ChatScreen';
import { FaceCaptureScreen } from './src/screens/FaceCaptureScreen';
import { ModaMainScreen } from './src/screens/ModaMainScreen';
import { ProfileInputScreen } from './src/screens/ProfileInputScreen';
import { ResultScreen } from './src/screens/ResultScreen';
import { StartScreen } from './src/screens/StartScreen';
import { colors } from './src/theme/theme';

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <NavigationContainer>
        <Stack.Navigator
          initialRouteName="Start"
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: colors.bg },
            animation: 'slide_from_right',
          }}
        >
          <Stack.Screen name="Start" component={StartScreen} />
          <Stack.Screen
            name="ProfileInput"
            component={ProfileInputScreen}
            options={{
              headerShown: true,
              title: '',
              headerTintColor: colors.text,
              headerStyle: { backgroundColor: colors.bg },
              headerShadowVisible: false,
            }}
          />
          <Stack.Screen name="FaceCapture" component={FaceCaptureScreen} />
          <Stack.Screen name="BodyCapture" component={BodyCaptureScreen} />
          <Stack.Screen
            name="Result"
            component={ResultScreen}
            options={{
              gestureEnabled: false, // 결과 화면에서 좌→우 엣지 스와이프로 카메라 복귀 방지
              headerBackVisible: false,
            }}
          />
          <Stack.Screen name="Chat" component={ChatScreen} />
          <Stack.Screen name="ModaMain" component={ModaMainScreen} />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
