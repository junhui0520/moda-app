import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { BackHandler, Platform, StyleSheet, View } from 'react-native';
import type { ResultData } from './src/api/types';
import { ChatScreen } from './src/screens/ChatScreen';
import { ResultScreen } from './src/screens/ResultScreen';
import { StartScreen } from './src/screens/StartScreen';
import { colors } from './src/theme';

// shortcut: 화면 3개라 상태로 전환. 입력·촬영 화면이 붙어 화면이 늘면 Expo Router로 옮길 것
type Screen =
  | { name: 'start' }
  | { name: 'result'; sessionId: string; result: ResultData }
  | { name: 'chat'; sessionId: string; result: ResultData };

export default function App() {
  const [screen, setScreen] = useState<Screen>({ name: 'start' });

  useEffect(() => {
    if (screen.name !== 'chat') return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      setScreen({ ...screen, name: 'result' });
      return true;
    });
    return () => sub.remove();
  }, [screen]);

  return (
    <View style={styles.root}>
      {screen.name === 'start' && (
        <StartScreen onDone={(sessionId, result) => setScreen({ name: 'result', sessionId, result })} />
      )}
      {screen.name === 'result' && (
        <ResultScreen
          result={screen.result}
          onChat={() => setScreen({ ...screen, name: 'chat' })}
          onRestart={() => setScreen({ name: 'start' })}
        />
      )}
      {screen.name === 'chat' && (
        <ChatScreen sessionId={screen.sessionId} onBack={() => setScreen({ ...screen, name: 'result' })} />
      )}
      <StatusBar style="dark" />
    </View>
  );
}

const styles = StyleSheet.create({
  // shortcut: safe-area 라이브러리 대신 고정 여백, 노치 대응이 어긋나면 react-native-safe-area-context 도입
  root: { flex: 1, backgroundColor: colors.bg, paddingTop: Platform.OS === 'web' ? 0 : 44 },
});
