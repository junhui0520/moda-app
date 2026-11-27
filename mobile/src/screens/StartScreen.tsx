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

        {/* 중앙 Apple 온보딩 스타일 헤드라인 */}
        <View style={styles.hero}>
          <View style={styles.appIcon}>
            <Text style={styles.appIconText}>M</Text>
          </View>
          <Text style={styles.title}>
            당신의 얼굴과 체형에{'\n'}가장 잘 어울리는 스타일
          </Text>
          <Text style={styles.subtitle}>
            간단한 촬영과 정보 입력만으로 맞춤 헤어와 패션을 추천해 드려요.
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
    paddingBottom: 12,
  },
  brand: {
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 1.5,
    color: colors.text,
  },
  mockBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.full,
  },
  mockBtnActive: {
    borderColor: colors.accentBorder,
    backgroundColor: colors.accentLight,
  },
  mockBtnText: {
    fontSize: 11.5,
    fontWeight: '600',
    color: colors.textMuted,
  },
  mockBtnTextActive: {
    color: colors.accent,
  },
  hero: {
    flex: 1,
    justifyContent: 'center',
    paddingBottom: 32,
  },
  appIcon: {
    width: 60,
    height: 60,
    borderRadius: 15,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
  },
  appIconText: {
    color: '#FFFFFF',
    fontSize: 28,
    fontWeight: '800',
  },
  title: {
    fontSize: 30,
    fontWeight: '700',
    color: colors.text,
    lineHeight: 38,
    letterSpacing: -0.6,
  },
  subtitle: {
    marginTop: 12,
    fontSize: 15,
    lineHeight: 22,
    color: colors.textSecondary,
  },
  bottomArea: {
    gap: 12,
  },
  consentRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
  },
  consentRowChecked: {
    borderColor: colors.primary,
    backgroundColor: colors.surface,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: radius.full,
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
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
  },
  consentSub: {
    marginTop: 3,
    fontSize: 12.5,
    color: colors.textSecondary,
    lineHeight: 18,
  },
  startBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: 'center',
  },
  startBtnDisabled: {
    opacity: 0.38,
  },
  startBtnText: {
    color: '#FFFFFF',
    fontSize: 16.5,
    fontWeight: '600',
  },
});
