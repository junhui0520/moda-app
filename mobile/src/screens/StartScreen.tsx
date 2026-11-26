import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import type { ResultData } from '../api/types';
import { runRandomDemo } from '../randomDemo';
import { colors } from '../theme';

export function StartScreen({ onDone }: { onDone: (sessionId: string, result: ResultData) => void }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function start() {
    setLoading(true);
    setError(null);
    try {
      const { sessionId, result } = await runRandomDemo();
      onDone(sessionId, result);
    } catch (e) {
      setError((e as Error).message);
      setLoading(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>Moda</Text>
      <Text style={styles.desc}>
        기본 정보 입력과 얼굴·체형 분석은 지금은 랜덤 값으로 대신합니다.{'\n'}버튼을 누르면 결과 화면으로 이동해요.
      </Text>
      <Pressable
        style={[styles.button, loading && styles.disabled]}
        onPress={start}
        disabled={loading}
        accessibilityRole="button"
      >
        {loading ? <ActivityIndicator color={colors.onPrimary} /> : <Text style={styles.buttonText}>랜덤으로 체험하기</Text>}
      </Pressable>
      {loading && <Text style={styles.desc}>분석 중이에요…</Text>}
      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 16 },
  logo: { fontSize: 40, fontWeight: '800', color: colors.primary },
  desc: { fontSize: 15, color: colors.subtext, textAlign: 'center', lineHeight: 22 },
  button: {
    backgroundColor: colors.primary,
    paddingVertical: 14,
    paddingHorizontal: 32,
    borderRadius: 12,
    minWidth: 220,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  disabled: { opacity: 0.6 },
  buttonText: { color: colors.onPrimary, fontSize: 16, fontWeight: '700' },
  error: { color: colors.danger, textAlign: 'center' },
});
