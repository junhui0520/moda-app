// src/screens/ModaMainScreen.tsx — A안 에디토리얼 Moda 메인 화면 (MVP 진입점 + 로고 3초 초기화)

import React from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/types';
import { useSessionStore } from '../store/useSessionStore';
import { colors, radius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'ModaMain'>;

export function ModaMainScreen({ navigation }: Props) {
  const { result, resetSessionWithApi } = useSessionStore();

  const handleHiddenBoothReset = () => {
    Alert.alert(
      '체험 초기화',
      '현재 세션과 대화 내역, 로컬 사진 정보를 모두 삭제하고 처음 화면으로 돌아갈까요?',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '초기화',
          style: 'destructive',
          onPress: async () => {
            await resetSessionWithApi();
            navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
          },
        },
      ],
    );
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.topRow}>
          <Pressable onLongPress={handleHiddenBoothReset} delayLongPress={3000}>
            <Text style={styles.brand}>MODA</Text>
          </Pressable>
          <Text style={styles.mvpBadge}>MVP</Text>
        </View>

        <View style={styles.summaryCard}>
          <Text style={styles.eyebrow}>MY STYLE TYPE</Text>
          <Text style={styles.typeName}>
            {result ? result.type.typeName : '맞춤 스타일 분석 완료'}
          </Text>
          {result ? (
            <Text style={styles.basisText}>
              {result.basis.faceShape.label} · {result.basis.bodyType.label}
            </Text>
          ) : null}

          <View style={styles.quickRow}>
            <Pressable style={styles.quickBtnOutline} onPress={() => navigation.navigate('Result')}>
              <Text style={styles.quickBtnOutlineText}>결과 다시 보기</Text>
            </Pressable>
            <Pressable style={styles.quickBtnFill} onPress={() => navigation.navigate('Chat')}>
              <Text style={styles.quickBtnFillText}>챗봇 이용하기</Text>
            </Pressable>
          </View>
        </View>

        <Text style={styles.sectionEyebrow}>COMING SOON</Text>
        <Text style={styles.sectionTitle}>확장 예정 기능</Text>

        <View style={styles.featureList}>
          {[
            {
              icon: 'heart-outline' as const,
              title: '스와이프 취향 탐색',
              desc: '마음에 드는 코디를 넘기며 취향 기반 패션 타입을 찾아요.',
            },
            {
              icon: 'shirt-outline' as const,
              title: '상황별 코디 룩북',
              desc: '소개팅·출근·데일리 상황에 맞는 코디 조합을 제안해요.',
            },
            {
              icon: 'share-outline' as const,
              title: '유형 카드 공유',
              desc: '분석 리포트 카드를 저장해 친구들과 비교해 보세요.',
            },
          ].map((item) => (
            <View key={item.title} style={styles.featureCard}>
              <View style={styles.iconBox}>
                <Ionicons name={item.icon} size={20} color={colors.text} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.featureTitle}>{item.title}</Text>
                <Text style={styles.featureDesc}>{item.desc}</Text>
              </View>
            </View>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  container: {
    padding: 20,
    paddingBottom: 36,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: 14,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    marginBottom: 20,
  },
  brand: {
    fontSize: 17,
    fontWeight: '900',
    letterSpacing: 3.5,
    color: colors.text,
  },
  mvpBadge: {
    fontSize: 10.5,
    fontWeight: '800',
    letterSpacing: 1.2,
    color: colors.textMuted,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: radius.xs,
  },
  summaryCard: {
    backgroundColor: colors.surfaceDark,
    borderRadius: radius.md,
    padding: 22,
    marginBottom: 28,
  },
  eyebrow: {
    fontSize: 10.5,
    fontWeight: '800',
    letterSpacing: 2,
    color: '#B8B0A4',
  },
  typeName: {
    marginTop: 8,
    fontSize: 24,
    fontWeight: '900',
    color: colors.textOnDark,
    letterSpacing: -0.4,
  },
  basisText: {
    marginTop: 4,
    fontSize: 13.5,
    color: '#DDD6CA',
  },
  quickRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 20,
  },
  quickBtnOutline: {
    flex: 1,
    borderWidth: 1,
    borderColor: 'rgba(250,248,243,0.35)',
    paddingVertical: 13,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  quickBtnOutlineText: {
    color: colors.textOnDark,
    fontSize: 13.5,
    fontWeight: '800',
  },
  quickBtnFill: {
    flex: 1,
    backgroundColor: colors.surface,
    paddingVertical: 13,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  quickBtnFillText: {
    color: colors.primary,
    fontSize: 13.5,
    fontWeight: '800',
  },
  sectionEyebrow: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2,
    color: colors.textMuted,
    marginBottom: 4,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '900',
    color: colors.text,
    marginBottom: 14,
  },
  featureList: {
    gap: 10,
  },
  featureCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    backgroundColor: colors.surface,
    borderRadius: radius.sm,
    padding: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  iconBox: {
    width: 40,
    height: 40,
    borderRadius: radius.xs,
    backgroundColor: colors.surfaceMuted,
    alignItems: 'center',
    justifyContent: 'center',
  },
  featureTitle: {
    fontSize: 14.5,
    fontWeight: '800',
    color: colors.text,
  },
  featureDesc: {
    marginTop: 3,
    fontSize: 12.5,
    color: colors.textSecondary,
    lineHeight: 18,
  },
});
