// src/screens/ResultScreen.tsx — iOS 네이티브 결과 화면 (시각화 요약 카드 + 실시간 동기화 캐러셀 + ⑤⑥⑧)

import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
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
  const [descExpanded, setDescExpanded] = useState(false);
  const [hairIndex, setHairIndex] = useState(0);
  const [fashionIndex, setFashionIndex] = useState(0);

  // 캐러셀 스와이프와 0ms 지연 없이 실시간 연동되는 스크롤 값
  const hairScrollX = useRef(new Animated.Value(0)).current;
  const fashionScrollX = useRef(new Animated.Value(0)).current;

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

  // 시연·재체험용 초기화 (처음부터 다시 측정하기 버튼 및 상단 제목 3초 길게 누르기 공용)
  const handleRestartFromBeginning = () => {
    Alert.alert(
      '처음부터 다시 측정할까요?',
      '현재 분석 결과와 챗봇 대화, 임시 사진 정보가 모두 초기화되고 처음 화면으로 돌아가요.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '처음으로',
          style: 'destructive',
          onPress: async () => {
            await resetSessionWithApi();
            navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
          },
        },
      ],
    );
  };

  const updateIndexOnScroll = (
    e: NativeSyntheticEvent<NativeScrollEvent>,
    total: number,
    current: number,
    setter: (idx: number) => void,
  ) => {
    const x = e.nativeEvent.contentOffset.x;
    const nextIdx = Math.max(0, Math.min(total - 1, Math.round(x / snapInterval)));
    if (nextIdx !== current) {
      setter(nextIdx);
    }
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

  const topHair = result.hairStyles[0];
  const topFashion = result.fashionStyles[0];

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
        {/* 상단 바 (3초 길게 누르기 → 부스 초기화) */}
        <View style={styles.topBar}>
          <Pressable onLongPress={handleRestartFromBeginning} delayLongPress={3000}>
            <Text style={styles.brand}>분석 결과</Text>
          </Pressable>
        </View>

        {/* 1. 맞춤 유형 시각화 요약 카드 */}
        <View style={styles.sectionPad}>
          <View style={styles.heroCard}>
            <View style={styles.heroTopMeta}>
              <Text style={styles.typeTag}>{result.type.typeName}</Text>
            </View>

            <Text style={styles.headline}>{result.type.headline}</Text>

            {/* 시각화 2x2 요약 그리드: 얼굴형 · 체형 · 베스트 헤어 · 베스트 패션 */}
            <View style={styles.visualGrid}>
              <View style={styles.visualTile}>
                <View style={styles.tileIconWrap}>
                  <Ionicons name="person-outline" size={16} color={colors.text} />
                </View>
                <Text style={styles.tileLabel}>얼굴형</Text>
                <Text style={styles.tileValue} numberOfLines={1}>
                  {result.basis.faceShape.label}
                </Text>
              </View>

              <View style={styles.visualTile}>
                <View style={styles.tileIconWrap}>
                  <Ionicons name="body-outline" size={16} color={colors.text} />
                </View>
                <Text style={styles.tileLabel}>체형</Text>
                <Text style={styles.tileValue} numberOfLines={1}>
                  {result.basis.bodyType.label}
                </Text>
              </View>

              {topHair ? (
                <View style={styles.visualTile}>
                  <View style={styles.tileIconWrap}>
                    <Ionicons name="cut-outline" size={16} color={colors.text} />
                  </View>
                  <Text style={styles.tileLabel}>베스트 헤어</Text>
                  <Text style={styles.tileValue} numberOfLines={1}>
                    {topHair.name}
                  </Text>
                </View>
              ) : null}

              {topFashion ? (
                <View style={styles.visualTile}>
                  <View style={styles.tileIconWrap}>
                    <Ionicons name="shirt-outline" size={16} color={colors.text} />
                  </View>
                  <Text style={styles.tileLabel}>베스트 패션</Text>
                  <Text style={styles.tileValue} numberOfLines={1}>
                    {topFashion.name}
                  </Text>
                </View>
              ) : null}
            </View>

            {/* 핵심 스타일 포인트 — 아이콘 기반 시각 카드 */}
            {result.type.stylePoints.length > 0 ? (
              <View style={styles.pointsBox}>
                {result.type.stylePoints.map((pt, idx) => (
                  <View key={idx} style={styles.pointRow}>
                    <View style={styles.pointIconBadge}>
                      <Ionicons
                        name={idx === 0 ? 'shirt-outline' : 'cut-outline'}
                        size={14}
                        color={colors.text}
                      />
                    </View>
                    <Text style={styles.pointText}>{pt}</Text>
                  </View>
                ))}
              </View>
            ) : null}

            {/* 유형 설명 접기/펼치기 (첫 화면 글 부담 줄이기) */}
            <Pressable
              style={styles.descToggleRow}
              onPress={() => setDescExpanded((prev) => !prev)}
            >
              <Text style={styles.descToggleText}>
                {descExpanded ? '유형 설명 접기' : '유형 설명 더보기'}
              </Text>
              <Ionicons
                name={descExpanded ? 'chevron-up' : 'chevron-down'}
                size={15}
                color={colors.textMuted}
              />
            </Pressable>

            {descExpanded ? (
              <Text style={styles.typeDesc}>{result.type.description}</Text>
            ) : null}

            <Pressable style={styles.detailLinkRow} onPress={() => setDetailVisible(true)}>
              <Text style={styles.detailLinkText}>상세정보 확인</Text>
              <Ionicons name="chevron-forward" size={18} color={colors.text} />
            </Pressable>
          </View>
        </View>

        {/* 2. 추천 헤어스타일 — 실시간 동기화 가로 스와이프 카드 */}
        <View style={[styles.sectionPad, styles.sectionHeader]}>
          <View>
            <Text style={styles.sectionEyebrow}>HAIR LOOKBOOK</Text>
            <Text style={styles.sectionTitle}>추천 헤어스타일</Text>
          </View>
          <Text style={styles.counterText}>
            0{hairIndex + 1} / 0{result.hairStyles.length}
          </Text>
        </View>

        <Animated.ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          decelerationRate="fast"
          snapToInterval={snapInterval}
          snapToAlignment="start"
          disableIntervalMomentum
          scrollEventThrottle={16}
          contentContainerStyle={styles.carouselContent}
          onScroll={Animated.event(
            [{ nativeEvent: { contentOffset: { x: hairScrollX } } }],
            {
              useNativeDriver: false,
              listener: (e: NativeSyntheticEvent<NativeScrollEvent>) =>
                updateIndexOnScroll(e, result.hairStyles.length, hairIndex, setHairIndex),
            },
          )}
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
        </Animated.ScrollView>

        {/* 손가락 이동과 실시간 동기화되는 페이지 인디케이터 */}
        <View style={styles.dotsRow}>
          {result.hairStyles.map((h, idx) => {
            const inputRange = [
              (idx - 1) * snapInterval,
              idx * snapInterval,
              (idx + 1) * snapInterval,
            ];
            const width = hairScrollX.interpolate({
              inputRange,
              outputRange: [6, 20, 6],
              extrapolate: 'clamp',
            });
            const opacity = hairScrollX.interpolate({
              inputRange,
              outputRange: [0.25, 1, 0.25],
              extrapolate: 'clamp',
            });
            return (
              <Animated.View
                key={h.code}
                style={[styles.dot, { width, opacity }]}
              />
            );
          })}
        </View>

        {/* 3. 추천 패션스타일 — 실시간 동기화 가로 스와이프 카드 */}
        <View style={[styles.sectionPad, styles.sectionHeader, { marginTop: 28 }]}>
          <View>
            <Text style={styles.sectionEyebrow}>FASHION LOOKBOOK</Text>
            <Text style={styles.sectionTitle}>추천 패션스타일</Text>
          </View>
          <Text style={styles.counterText}>
            0{fashionIndex + 1} / 0{result.fashionStyles.length}
          </Text>
        </View>

        <Animated.ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          decelerationRate="fast"
          snapToInterval={snapInterval}
          snapToAlignment="start"
          disableIntervalMomentum
          scrollEventThrottle={16}
          contentContainerStyle={styles.carouselContent}
          onScroll={Animated.event(
            [{ nativeEvent: { contentOffset: { x: fashionScrollX } } }],
            {
              useNativeDriver: false,
              listener: (e: NativeSyntheticEvent<NativeScrollEvent>) =>
                updateIndexOnScroll(e, result.fashionStyles.length, fashionIndex, setFashionIndex),
            },
          )}
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
        </Animated.ScrollView>

        {/* 손가락 이동과 실시간 동기화되는 페이지 인디케이터 */}
        <View style={styles.dotsRow}>
          {result.fashionStyles.map((f, idx) => {
            const inputRange = [
              (idx - 1) * snapInterval,
              idx * snapInterval,
              (idx + 1) * snapInterval,
            ];
            const width = fashionScrollX.interpolate({
              inputRange,
              outputRange: [6, 20, 6],
              extrapolate: 'clamp',
            });
            const opacity = fashionScrollX.interpolate({
              inputRange,
              outputRange: [0.25, 1, 0.25],
              extrapolate: 'clamp',
            });
            return (
              <Animated.View
                key={f.code}
                style={[styles.dot, { width, opacity }]}
              />
            );
          })}
        </View>

        {/* 하단 CTA — 챗봇 이용하기 + 시연용 처음부터 다시 측정하기 */}
        <View style={[styles.sectionPad, styles.ctaBox]}>
          <Pressable
            style={styles.primaryCta}
            onPress={() => navigation.navigate('Chat')}
          >
            <Text style={styles.primaryCtaText}>챗봇 이용하기</Text>
          </Pressable>

          <Pressable
            style={styles.secondaryCta}
            onPress={handleRestartFromBeginning}
          >
            <Text style={styles.secondaryCtaText}>처음부터 다시 측정하기</Text>
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
    paddingTop: 12,
    paddingBottom: 14,
  },
  brand: {
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: -0.6,
    color: colors.text,
  },
  heroCard: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: 20,
    marginBottom: 24,
  },
  heroTopMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  typeTag: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.text,
    backgroundColor: colors.bg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: radius.full,
    overflow: 'hidden',
  },
  headline: {
    fontSize: 21,
    fontWeight: '700',
    color: colors.text,
    lineHeight: 29,
    letterSpacing: -0.4,
  },
  visualGrid: {
    marginTop: 16,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  visualTile: {
    width: '48.5%',
    backgroundColor: colors.bg,
    borderRadius: radius.sm,
    padding: 12,
    gap: 4,
  },
  tileIconWrap: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  tileLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: colors.textMuted,
  },
  tileValue: {
    fontSize: 14.5,
    fontWeight: '700',
    color: colors.text,
  },
  pointsBox: {
    marginTop: 12,
    gap: 8,
  },
  pointRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    backgroundColor: colors.bg,
    borderRadius: radius.sm,
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  pointIconBadge: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 1,
  },
  pointText: {
    flex: 1,
    fontSize: 13,
    color: colors.text,
    lineHeight: 19,
  },
  descToggleRow: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    paddingVertical: 6,
  },
  descToggleText: {
    fontSize: 12.5,
    fontWeight: '600',
    color: colors.textMuted,
  },
  typeDesc: {
    marginTop: 4,
    fontSize: 13.5,
    color: colors.textSecondary,
    lineHeight: 20,
    backgroundColor: colors.bg,
    padding: 12,
    borderRadius: radius.sm,
  },
  detailLinkRow: {
    marginTop: 12,
    paddingTop: 14,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  detailLinkText: {
    fontSize: 15,
    fontWeight: '700',
    color: colors.text,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  sectionEyebrow: {
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.6,
    color: colors.textMuted,
    marginBottom: 2,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: colors.text,
    letterSpacing: -0.4,
  },
  counterText: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textMuted,
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
    fontWeight: '700',
    color: colors.text,
    letterSpacing: -0.3,
  },
  lookDesc: {
    marginTop: 4,
    fontSize: 13.5,
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
    backgroundColor: colors.bg,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: radius.full,
  },
  keywordText: {
    fontSize: 12,
    fontWeight: '500',
    color: colors.textSecondary,
  },
  reasonsDivider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 12,
  },
  reasonsList: {
    gap: 6,
  },
  reasonText: {
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  dotsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    marginTop: 12,
  },
  dot: {
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.primary,
  },
  ctaBox: {
    marginTop: 28,
    gap: 10,
  },
  primaryCta: {
    backgroundColor: colors.primary,
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: 'center',
  },
  primaryCtaText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryCta: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 15,
    borderRadius: 14,
    alignItems: 'center',
  },
  secondaryCtaText: {
    color: colors.text,
    fontSize: 15.5,
    fontWeight: '600',
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
