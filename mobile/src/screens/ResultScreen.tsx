// src/screens/ResultScreen.tsx — A안 매거진 에디토리얼 결과 화면 (가로 1열 스와이프 화보 카드 + 깔끔한 CTA + ⑤⑥⑧)

import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  NativeScrollEvent,
  NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { isModaApiError } from '../api/client';
import { modaApi } from '../api/modaApi';
import { AnalysisDetailModal } from '../components/AnalysisDetailModal';
import { StyleImageCard } from '../components/StyleImageCard';
import type { RootStackParamList } from '../navigation/types';
import { useSessionStore } from '../store/useSessionStore';
import { colors, radius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Result'>;

const CARD_GAP = 12;
const HORIZONTAL_PADDING = 20;

export function ResultScreen({ navigation }: Props) {
  const { width: screenWidth } = useWindowDimensions();
  const cardWidth = Math.min(screenWidth - HORIZONTAL_PADDING * 2 - 20, 380);
  const snapInterval = cardWidth + CARD_GAP;

  const {
    sessionId,
    baseUrl,
    result,
    setResult,
    resetSessionLocal,
    resetSessionWithApi,
  } = useSessionStore();

  const [loading, setLoading] = useState(!result);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [hairIndex, setHairIndex] = useState(0);
  const [fashionIndex, setFashionIndex] = useState(0);

  const handleSessionExpired = () => {
    Alert.alert('세션 만료', '체험 시간이 지나 세션이 종료되었어요. 처음부터 다시 시작해 주세요.', [
      {
        text: '처음으로',
        onPress: () => {
          resetSessionLocal();
          navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
        },
      },
    ]);
  };

  const fetchResult = async () => {
    if (!sessionId) {
      resetSessionLocal();
      navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
      return;
    }

    setLoading(true);
    setErrorMsg(null);
    try {
      const data = await modaApi.getResult(sessionId, baseUrl);
      setResult(data);
    } catch (err) {
      if (isModaApiError(err)) {
        if (err.isSessionExpired()) {
          handleSessionExpired();
          return;
        }
        if (err.code === 'ANALYSIS_INCOMPLETE') {
          const next = err.meta?.nextStep;
          Alert.alert('분석 미완료', err.message, [
            {
              text: '이어서 진행',
              onPress: () => {
                if (next === 'PROFILE_INPUT') navigation.navigate('ProfileInput');
                else if (next === 'FACE_ANALYSIS') navigation.navigate('FaceCapture');
                else navigation.navigate('BodyCapture');
              },
            },
          ]);
          return;
        }
      }
      setErrorMsg(err instanceof Error ? err.message : '결과를 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchResult();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ⑧ 부스 초기화 (숨김 기능: 상단 MODA 로고 3초 길게 누르기)
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

  const handleCarouselScroll = (
    e: NativeSyntheticEvent<NativeScrollEvent>,
    total: number,
    setter: (idx: number) => void,
  ) => {
    const x = e.nativeEvent.contentOffset.x;
    const idx = Math.max(0, Math.min(total - 1, Math.round(x / snapInterval)));
    setter(idx);
  };

  if (loading && !result) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.loadingText}>맞춤 리포트를 불러오고 있어요...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (errorMsg && !result) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centerBox}>
          <Text style={styles.errorText}>{errorMsg}</Text>
          <Pressable style={styles.retryBtn} onPress={fetchResult}>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    );
  }

  if (!result) return null;

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        {/* 상단 바 (로고 3초 길게 누르기 → 부스 초기화) */}
        <View style={styles.topBar}>
          <Pressable onLongPress={handleHiddenBoothReset} delayLongPress={3000}>
            <Text style={styles.brand}>MODA</Text>
          </Pressable>

          <Pressable style={styles.headerDetailBtn} onPress={() => setDetailVisible(true)}>
            <Text style={styles.headerDetailText}>상세정보 확인</Text>
          </Pressable>
        </View>

        {/* 1. 맞춤 유형 에디토리얼 히어로 카드 */}
        <View style={styles.sectionPad}>
          <View style={styles.heroCard}>
            <View style={styles.heroTopMeta}>
              <Text style={styles.typeTag}>{result.type.typeName}</Text>
              <Text style={styles.basisInline}>
                {result.basis.faceShape.label} · {result.basis.bodyType.label}
              </Text>
            </View>

            <Text style={styles.headline}>{result.type.headline}</Text>
            <Text style={styles.typeDesc}>{result.type.description}</Text>

            {result.type.stylePoints.length > 0 ? (
              <View style={styles.pointsBox}>
                {result.type.stylePoints.map((pt, idx) => (
                  <View key={idx} style={styles.pointRow}>
                    <Text style={styles.pointIdx}>0{idx + 1}</Text>
                    <Text style={styles.pointText}>{pt}</Text>
                  </View>
                ))}
              </View>
            ) : null}

            <Pressable style={styles.detailLinkRow} onPress={() => setDetailVisible(true)}>
              <Text style={styles.detailLinkText}>상세정보 확인</Text>
              <Ionicons name="arrow-forward" size={15} color="#FFFFFF" />
            </Pressable>
          </View>
        </View>

        {/* 2. 추천 헤어스타일 — 가로 1열 스와이프 화보 카드 */}
        <View style={[styles.sectionPad, styles.sectionHeader]}>
          <View>
            <Text style={styles.sectionEyebrow}>HAIR LOOKBOOK</Text>
            <Text style={styles.sectionTitle}>추천 헤어스타일</Text>
          </View>
          <Text style={styles.counterText}>
            0{hairIndex + 1} / 0{result.hairStyles.length}
          </Text>
        </View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          decelerationRate="fast"
          snapToInterval={snapInterval}
          snapToAlignment="start"
          contentContainerStyle={styles.carouselContent}
          onMomentumScrollEnd={(e) =>
            handleCarouselScroll(e, result.hairStyles.length, setHairIndex)
          }
          onScrollEndDrag={(e) =>
            handleCarouselScroll(e, result.hairStyles.length, setHairIndex)
          }
        >
          {result.hairStyles.map((hair) => (
            <View key={hair.code} style={[styles.lookCard, { width: cardWidth }]}>
              <StyleImageCard
                imageKey={hair.imageKey}
                label={hair.name}
                rank={hair.rank}
                total={result.hairStyles.length}
                height={210}
              />
              <View style={styles.lookCardBody}>
                <Text style={styles.lookName}>{hair.name}</Text>
                <Text style={styles.lookDesc}>{hair.description}</Text>

                <View style={styles.reasonsDivider} />
                <View style={styles.reasonsList}>
                  {hair.reasons.map((reason, i) => (
                    <Text key={i} style={styles.reasonText}>
                      · {reason}
                    </Text>
                  ))}
                </View>
              </View>
            </View>
          ))}
        </ScrollView>

        <View style={styles.dotsRow}>
          {result.hairStyles.map((h, idx) => (
            <View
              key={h.code}
              style={[styles.dot, idx === hairIndex && styles.dotActive]}
            />
          ))}
        </View>

        {/* 3. 추천 패션스타일 — 가로 1열 스와이프 화보 카드 */}
        <View style={[styles.sectionPad, styles.sectionHeader, { marginTop: 28 }]}>
          <View>
            <Text style={styles.sectionEyebrow}>FASHION LOOKBOOK</Text>
            <Text style={styles.sectionTitle}>추천 패션스타일</Text>
          </View>
          <Text style={styles.counterText}>
            0{fashionIndex + 1} / 0{result.fashionStyles.length}
          </Text>
        </View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          decelerationRate="fast"
          snapToInterval={snapInterval}
          snapToAlignment="start"
          contentContainerStyle={styles.carouselContent}
          onMomentumScrollEnd={(e) =>
            handleCarouselScroll(e, result.fashionStyles.length, setFashionIndex)
          }
          onScrollEndDrag={(e) =>
            handleCarouselScroll(e, result.fashionStyles.length, setFashionIndex)
          }
        >
          {result.fashionStyles.map((fashion) => (
            <View key={fashion.code} style={[styles.lookCard, { width: cardWidth }]}>
              <StyleImageCard
                imageKey={fashion.imageKey}
                label={fashion.name}
                rank={fashion.rank}
                total={result.fashionStyles.length}
                height={230}
              />
              <View style={styles.lookCardBody}>
                <Text style={styles.lookName}>{fashion.name}</Text>
                <Text style={styles.lookDesc}>{fashion.description}</Text>

                {fashion.itemKeywords.length > 0 ? (
                  <View style={styles.keywordsWrap}>
                    {fashion.itemKeywords.map((kw, idx) => (
                      <View key={`${kw}-${idx}`} style={styles.keywordChip}>
                        <Text style={styles.keywordText}>#{kw}</Text>
                      </View>
                    ))}
                  </View>
                ) : null}

                <View style={styles.reasonsDivider} />
                <View style={styles.reasonsList}>
                  {fashion.reasons.map((reason, i) => (
                    <Text key={i} style={styles.reasonText}>
                      · {reason}
                    </Text>
                  ))}
                </View>
              </View>
            </View>
          ))}
        </ScrollView>

        <View style={styles.dotsRow}>
          {result.fashionStyles.map((f, idx) => (
            <View
              key={f.code}
              style={[styles.dot, idx === fashionIndex && styles.dotActive]}
            />
          ))}
        </View>

        {/* 하단 CTA — 깔끔하고 간결하게 */}
        <View style={[styles.sectionPad, styles.ctaBox]}>
          <Pressable
            style={styles.primaryCta}
            onPress={() => navigation.navigate('Chat')}
          >
            <Text style={styles.primaryCtaText}>챗봇 이용하기</Text>
          </Pressable>

          <Pressable
            style={styles.secondaryCta}
            onPress={() => navigation.navigate('ModaMain')}
          >
            <Text style={styles.secondaryCtaText}>Moda 시작하기</Text>
          </Pressable>
        </View>

        <Text style={styles.disclaimer}>{result.disclaimer}</Text>
      </ScrollView>

      <AnalysisDetailModal
        visible={detailVisible}
        onClose={() => setDetailVisible(false)}
        onSessionExpired={handleSessionExpired}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  centerBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  loadingText: {
    marginTop: 14,
    fontSize: 14,
    color: colors.textSecondary,
  },
  errorText: {
    fontSize: 14,
    color: colors.danger,
    textAlign: 'center',
    marginBottom: 14,
  },
  retryBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: radius.sm,
  },
  retryText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  container: {
    paddingBottom: 44,
  },
  sectionPad: {
    paddingHorizontal: HORIZONTAL_PADDING,
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: HORIZONTAL_PADDING,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    marginBottom: 18,
  },
  brand: {
    fontSize: 17,
    fontWeight: '900',
    letterSpacing: 3.5,
    color: colors.text,
  },
  headerDetailBtn: {
    borderWidth: 1,
    borderColor: colors.borderStrong,
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: radius.xs,
  },
  headerDetailText: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.text,
  },
  heroCard: {
    backgroundColor: colors.surfaceDark,
    borderRadius: radius.md,
    padding: 22,
    marginBottom: 28,
  },
  heroTopMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    marginBottom: 14,
  },
  typeTag: {
    fontSize: 11.5,
    fontWeight: '800',
    color: '#FFFFFF',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.28)',
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: radius.xs,
  },
  basisInline: {
    fontSize: 12,
    fontWeight: '600',
    color: '#A1A1AA',
  },
  headline: {
    fontSize: 22,
    fontWeight: '900',
    color: '#FFFFFF',
    lineHeight: 30,
    letterSpacing: -0.5,
  },
  typeDesc: {
    marginTop: 10,
    fontSize: 13.5,
    color: '#D4D4D8',
    lineHeight: 20,
  },
  pointsBox: {
    marginTop: 16,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.12)',
    gap: 8,
  },
  pointRow: {
    flexDirection: 'row',
    gap: 10,
  },
  pointIdx: {
    fontSize: 11,
    fontWeight: '800',
    color: '#71717A',
    marginTop: 2,
  },
  pointText: {
    flex: 1,
    fontSize: 12.5,
    color: '#E4E4E7',
    lineHeight: 19,
  },
  detailLinkRow: {
    marginTop: 18,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.12)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  detailLinkText: {
    fontSize: 13.5,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  sectionEyebrow: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2,
    color: colors.textMuted,
    marginBottom: 3,
  },
  sectionTitle: {
    fontSize: 19,
    fontWeight: '900',
    color: colors.text,
    letterSpacing: -0.4,
  },
  counterText: {
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1,
    color: colors.textSecondary,
  },
  carouselContent: {
    paddingHorizontal: HORIZONTAL_PADDING,
    gap: CARD_GAP,
  },
  lookCard: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    overflow: 'hidden',
  },
  lookCardBody: {
    padding: 16,
  },
  lookName: {
    fontSize: 18,
    fontWeight: '900',
    color: colors.text,
    letterSpacing: -0.3,
  },
  lookDesc: {
    marginTop: 5,
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  keywordsWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginTop: 12,
  },
  keywordChip: {
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: radius.xs,
  },
  keywordText: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.text,
  },
  reasonsDivider: {
    height: 1,
    backgroundColor: colors.border,
    marginVertical: 12,
  },
  reasonsList: {
    gap: 6,
  },
  reasonText: {
    fontSize: 12.5,
    color: colors.textSecondary,
    lineHeight: 18.5,
  },
  dotsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    marginTop: 12,
  },
  dot: {
    width: 6,
    height: 2.5,
    backgroundColor: colors.borderStrong,
  },
  dotActive: {
    width: 20,
    backgroundColor: colors.primary,
  },
  ctaBox: {
    marginTop: 30,
    gap: 10,
  },
  primaryCta: {
    backgroundColor: colors.primary,
    paddingVertical: 16,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  primaryCtaText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '800',
  },
  secondaryCta: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.primary,
    paddingVertical: 15,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  secondaryCtaText: {
    color: colors.primary,
    fontSize: 15,
    fontWeight: '800',
  },
  disclaimer: {
    marginTop: 20,
    paddingHorizontal: HORIZONTAL_PADDING,
    fontSize: 11.5,
    color: colors.textMuted,
    textAlign: 'center',
    lineHeight: 17,
  },
});
