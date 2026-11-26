// src/screens/StartScreen.tsx — A안 매거진 에디토리얼 시작 화면 (미니멀 커버 + 동의 + ① 세션 생성)

import React, { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { modaApi } from '../api/modaApi';
import { MockSettingsModal } from '../components/MockSettingsModal';
import type { RootStackParamList } from '../navigation/types';
import { useSessionStore } from '../store/useSessionStore';
import { colors, radius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Start'>;

export function StartScreen({ navigation }: Props) {
  const { baseUrl, mockConfig, setSession } = useSessionStore();
  const [consent, setConsent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [mockModalVisible, setMockModalVisible] = useState(false);

  const hasActiveMock = Boolean(
    mockConfig.faceShape ||
      mockConfig.bodyType ||
      mockConfig.unusable ||
      mockConfig.borderline ||
      mockConfig.bodyMinimal ||
      mockConfig.delayMs !== null,
  );

  const handleStart = async () => {
    if (!consent) {
      Alert.alert('동의가 필요해요', '사진 분석 동의에 체크해 주세요.');
      return;
    }

    setLoading(true);
    try {
      const data = await modaApi.createGuestSession(
        { photoConsent: true, deviceLabel: 'moda-mobile' },
        baseUrl,
      );
      setSession({
        sessionId: data.sessionId,
        expiresAt: data.expiresAt,
        progress: data.progress,
        nextStep: data.nextStep,
      });
      navigation.navigate('ProfileInput');
    } catch (err) {
      Alert.alert(
        '연결 실패',
        err instanceof Error ? err.message : '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        {/* 상단 브랜드 바 */}
        <View style={styles.topRow}>
          <Text style={styles.brand}>MODA</Text>
          <Pressable
            style={[styles.mockBtn, hasActiveMock && styles.mockBtnActive]}
            onPress={() => setMockModalVisible(true)}
          >
            <Ionicons
              name="options-outline"
              size={14}
              color={hasActiveMock ? colors.text : colors.textMuted}
            />
            <Text style={[styles.mockBtnText, hasActiveMock && styles.mockBtnTextActive]}>
              {hasActiveMock ? 'MOCK' : 'TEST'}
            </Text>
          </Pressable>
        </View>

        {/* 중앙 에디토리얼 헤드라인 — 군더더기 없이 딱 핵심만 */}
        <View style={styles.hero}>
          <View style={styles.accentBar} />
          <Text style={styles.eyebrow}>PERSONAL STYLE REPORT</Text>
          <Text style={styles.title}>
            당신의 얼굴과 체형에{'\n'}가장 잘 어울리는 무드
          </Text>
        </View>

        {/* 하단 동의 + 시작 버튼 */}
        <View style={styles.bottomArea}>
          <Pressable
            style={[styles.consentRow, consent && styles.consentRowChecked]}
            onPress={() => setConsent((prev) => !prev)}
          >
            <View style={[styles.checkbox, consent && styles.checkboxChecked]}>
              {consent ? <Ionicons name="checkmark" size={14} color="#FFFFFF" /> : null}
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.consentTitle}>[필수] 사진 분석 동의</Text>
              <Text style={styles.consentSub}>
                촬영한 사진은 분석 직후 즉시 삭제되며 저장되지 않습니다.
              </Text>
            </View>
          </Pressable>

          <Pressable
            style={[styles.startBtn, (!consent || loading) && styles.startBtnDisabled]}
            onPress={handleStart}
            disabled={!consent || loading}
          >
            {loading ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.startBtnText}>시작하기</Text>
            )}
          </Pressable>
        </View>
      </View>

      <MockSettingsModal
        visible={mockModalVisible}
        onClose={() => setMockModalVisible(false)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  container: {
    flex: 1,
    paddingHorizontal: 24,
    paddingTop: 14,
    paddingBottom: 28,
    justifyContent: 'space-between',
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: 14,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  brand: {
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 4,
    color: colors.text,
  },
  mockBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.xs,
  },
  mockBtnActive: {
    borderColor: colors.primary,
    backgroundColor: colors.surfaceMuted,
  },
  mockBtnText: {
    fontSize: 10.5,
    fontWeight: '700',
    letterSpacing: 1,
    color: colors.textMuted,
  },
  mockBtnTextActive: {
    color: colors.text,
  },
  hero: {
    flex: 1,
    justifyContent: 'center',
    paddingBottom: 40,
  },
  accentBar: {
    width: 32,
    height: 2.5,
    backgroundColor: colors.primary,
    marginBottom: 20,
  },
  eyebrow: {
    fontSize: 11.5,
    fontWeight: '800',
    letterSpacing: 2.2,
    color: colors.textMuted,
    marginBottom: 14,
  },
  title: {
    fontSize: 32,
    fontWeight: '900',
    color: colors.text,
    lineHeight: 43,
    letterSpacing: -0.9,
  },
  bottomArea: {
    gap: 12,
  },
  consentRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    padding: 15,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    backgroundColor: colors.surface,
  },
  consentRowChecked: {
    borderColor: colors.primary,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: radius.xs,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 1,
  },
  checkboxChecked: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  consentTitle: {
    fontSize: 13.5,
    fontWeight: '800',
    color: colors.text,
  },
  consentSub: {
    marginTop: 3,
    fontSize: 12,
    color: colors.textSecondary,
    lineHeight: 17,
  },
  startBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 17,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  startBtnDisabled: {
    opacity: 0.35,
  },
  startBtnText: {
    color: '#FFFFFF',
    fontSize: 15.5,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
});
