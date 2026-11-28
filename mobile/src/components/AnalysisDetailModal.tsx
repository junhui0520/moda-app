// src/components/AnalysisDetailModal.tsx — A안 에디토리얼 ⑥ 상세정보 확인 + ⑦ 결과 수정

import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { isModaApiError } from '../api/client';
import { modaApi } from '../api/modaApi';
import type {
  AnalysisDetailData,
  BodyTypeKey,
  FaceShapeKey,
  HairCurl,
  HairDensity,
  HairThickness,
  SizeKoreaItem,
} from '../api/types';
import { useSessionStore } from '../store/useSessionStore';
import { SlidingSegmentControl } from './SlidingSegmentControl';
import { colors, radius } from '../theme/theme';

interface AnalysisDetailModalProps {
  visible: boolean;
  onClose: () => void;
  onSessionExpired: () => void;
}

const HAIR_THICKNESS_OPTIONS: { value: HairThickness; label: string }[] = [
  { value: 'THIN', label: '가는 편' },
  { value: 'MEDIUM', label: '보통' },
  { value: 'THICK', label: '굵은 편' },
];
const HAIR_CURL_OPTIONS: { value: HairCurl; label: string }[] = [
  { value: 'STRAIGHT', label: '직모' },
  { value: 'SEMI_CURLY', label: '반곱슬' },
  { value: 'CURLY', label: '곱슬' },
];
const HAIR_DENSITY_OPTIONS: { value: HairDensity; label: string }[] = [
  { value: 'LOW', label: '적은 편' },
  { value: 'MEDIUM', label: '보통' },
  { value: 'HIGH', label: '많은 편' },
];

function formatSizeKoreaDirection(item: SizeKoreaItem): string {
  const absVal = Math.abs(item.deviationPercent).toFixed(1);
  if (item.direction === 'SIMILAR') return '평균과 비슷해요';
  if (item.direction === 'ABOVE') return `평균보다 ${absVal}% 커요`;
  return `평균보다 ${absVal}% 작아요`;
}

function formatFeatureValue(value: number, unit: 'ratio' | 'deg' | 'percent'): string {
  if (unit === 'deg') return `${value.toFixed(1)}°`;
  if (unit === 'percent') return `${value.toFixed(1)}%`;
  return value.toFixed(2);
}

function labelHairThickness(v: HairThickness): string {
  return HAIR_THICKNESS_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

function labelHairCurl(v: HairCurl): string {
  return HAIR_CURL_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

function labelHairDensity(v: HairDensity): string {
  return HAIR_DENSITY_OPTIONS.find((o) => o.value === v)?.label ?? v;
}

/** 중앙(평균 0%) 기준 ±15% 스케일로 편차 막대 너비(0~50%) 산출 */
function getDivergingBarWidthPercent(deviationPercent: number): number {
  const clamped = Math.min(15, Math.abs(deviationPercent));
  return Math.max(4, Math.round((clamped / 15) * 50));
}

export function AnalysisDetailModal({
  visible,
  onClose,
  onSessionExpired,
}: AnalysisDetailModalProps) {
  const {
    sessionId,
    baseUrl,
    localFaceUri,
    localBodyUri,
    analysisDetail,
    setAnalysisDetail,
    syncAfterPatch,
    setProfileState,
  } = useSessionStore();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [editMode, setEditMode] = useState(false);

  const [draftFace, setDraftFace] = useState<FaceShapeKey | null>(null);
  const [draftBody, setDraftBody] = useState<BodyTypeKey | null>(null);
  const [draftThickness, setDraftThickness] = useState<HairThickness | null>(null);
  const [draftCurl, setDraftCurl] = useState<HairCurl | null>(null);
  const [draftDensity, setDraftDensity] = useState<HairDensity | null>(null);

  const syncDrafts = (detail: AnalysisDetailData) => {
    setDraftFace(detail.face.result?.key ?? null);
    setDraftBody(detail.body.result?.key ?? null);
    setDraftThickness(detail.profileInput?.hairThickness ?? null);
    setDraftCurl(detail.profileInput?.hairCurl ?? null);
    setDraftDensity(detail.profileInput?.hairDensity ?? null);
  };

  const fetchDetail = async () => {
    if (!sessionId) return;
    setLoading(true);
    setErrorMsg(null);
    try {
      const detail = await modaApi.getAnalysisDetail(sessionId, baseUrl);
      setAnalysisDetail(detail);
      syncDrafts(detail);
    } catch (err) {
      if (isModaApiError(err) && err.isSessionExpired()) {
        onClose();
        onSessionExpired();
        return;
      }
      setErrorMsg(err instanceof Error ? err.message : '상세 정보를 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (visible) {
      setEditMode(false);
      fetchDetail();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  const handleSavePatch = async () => {
    if (!sessionId || !analysisDetail) return;

    const payload: {
      faceShape?: FaceShapeKey;
      bodyType?: BodyTypeKey;
      hairThickness?: HairThickness;
      hairCurl?: HairCurl;
      hairDensity?: HairDensity;
    } = {};

    if (draftFace && draftFace !== analysisDetail.face.result?.key) payload.faceShape = draftFace;
    if (draftBody && draftBody !== analysisDetail.body.result?.key) payload.bodyType = draftBody;
    if (draftThickness && draftThickness !== analysisDetail.profileInput?.hairThickness) {
      payload.hairThickness = draftThickness;
    }
    if (draftCurl && draftCurl !== analysisDetail.profileInput?.hairCurl) {
      payload.hairCurl = draftCurl;
    }
    if (draftDensity && draftDensity !== analysisDetail.profileInput?.hairDensity) {
      payload.hairDensity = draftDensity;
    }

    if (Object.keys(payload).length === 0) {
      setEditMode(false);
      return;
    }

    setSaving(true);
    try {
      const patched = await modaApi.patchProfile(sessionId, payload, baseUrl);
      syncAfterPatch({
        progress: patched.progress,
        nextStep: patched.nextStep,
        expiresAt: patched.expiresAt,
        result: patched.result,
      });

      if (analysisDetail.profileInput) {
        setProfileState({
          profileInput: {
            ...analysisDetail.profileInput,
            hairThickness: draftThickness ?? analysisDetail.profileInput.hairThickness,
            hairCurl: draftCurl ?? analysisDetail.profileInput.hairCurl,
            hairDensity: draftDensity ?? analysisDetail.profileInput.hairDensity,
          },
          progress: patched.progress,
          nextStep: patched.nextStep,
          expiresAt: patched.expiresAt,
        });
      }

      setEditMode(false);
      Alert.alert('적용 완료', '수정된 기준으로 결과가 바로 업데이트되었어요.', [
        { text: '확인', onPress: onClose },
      ]);
    } catch (err) {
      if (isModaApiError(err) && err.isSessionExpired()) {
        onClose();
        onSessionExpired();
        return;
      }
      Alert.alert('수정 실패', err instanceof Error ? err.message : '다시 시도해 주세요.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={styles.root}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>상세정보 확인</Text>
          <Pressable onPress={onClose} style={styles.closeBtn}>
            <Ionicons name="close" size={24} color={colors.text} />
          </Pressable>
        </View>

        {loading && !analysisDetail ? (
          <View style={styles.centerBox}>
            <ActivityIndicator size="large" color={colors.primary} />
            <Text style={styles.loadingText}>분석 정보를 불러오고 있어요...</Text>
          </View>
        ) : errorMsg ? (
          <View style={styles.centerBox}>
            <Text style={styles.errorText}>{errorMsg}</Text>
            <Pressable style={styles.retryBtn} onPress={fetchDetail}>
              <Text style={styles.retryText}>다시 시도</Text>
            </Pressable>
          </View>
        ) : analysisDetail ? (
          <ScrollView contentContainerStyle={styles.content}>
            <SlidingSegmentControl
              options={[
                { value: 'detail', label: '분석 상세' },
                { value: 'edit', label: '결과 수정' },
              ]}
              value={editMode ? 'edit' : 'detail'}
              onChange={(v) => setEditMode(v === 'edit')}
            />

            {!editMode ? (
              <>
                {/* 0-1. 로컬 촬영 사진 썸네일 (R-01: 서버 미저장, 기기 로컬 URI만 표시) */}
                {localFaceUri || localBodyUri ? (
                  <View style={styles.card}>
                    <Text style={styles.cardEyebrow}>CAPTURED PHOTOS</Text>
                    <View style={styles.photoRow}>
                      {localFaceUri ? (
                        <View style={styles.photoSlot}>
                          <Image
                            source={{ uri: localFaceUri }}
                            style={styles.photoThumb}
                            resizeMode="cover"
                          />
                          <Text style={styles.photoCaption}>얼굴 사진</Text>
                        </View>
                      ) : null}
                      {localBodyUri ? (
                        <View style={styles.photoSlot}>
                          <Image
                            source={{ uri: localBodyUri }}
                            style={styles.photoThumb}
                            resizeMode="cover"
                          />
                          <Text style={styles.photoCaption}>전신 사진</Text>
                        </View>
                      ) : null}
                    </View>
                    <Text style={styles.privacyNote}>
                      촬영 사진은 기기에서만 임시 표시되며 서버에 저장되지 않아요.
                    </Text>
                  </View>
                ) : null}

                {/* 0-2. 입력 기본정보 · 모질 요약 (R-11: 몸무게·BMI는 절대 비노출) */}
                {analysisDetail.profileInput ? (
                  <View style={styles.card}>
                    <Text style={styles.cardEyebrow}>MY PROFILE</Text>
                    <View style={styles.profileGrid}>
                      <View style={styles.profileStat}>
                        <Text style={styles.profileStatLabel}>나이</Text>
                        <Text style={styles.profileStatVal}>
                          {analysisDetail.profileInput.age}세
                        </Text>
                      </View>
                      <View style={styles.profileStat}>
                        <Text style={styles.profileStatLabel}>키</Text>
                        <Text style={styles.profileStatVal}>
                          {analysisDetail.profileInput.heightCm}cm
                        </Text>
                      </View>
                    </View>
                    <View style={styles.hairSummaryRow}>
                      <View style={styles.hairTag}>
                        <Text style={styles.hairTagKey}>굵기</Text>
                        <Text style={styles.hairTagVal}>
                          {labelHairThickness(analysisDetail.profileInput.hairThickness)}
                        </Text>
                      </View>
                      <View style={styles.hairTag}>
                        <Text style={styles.hairTagKey}>곱슬</Text>
                        <Text style={styles.hairTagVal}>
                          {labelHairCurl(analysisDetail.profileInput.hairCurl)}
                        </Text>
                      </View>
                      <View style={styles.hairTag}>
                        <Text style={styles.hairTagKey}>머리숱</Text>
                        <Text style={styles.hairTagVal}>
                          {labelHairDensity(analysisDetail.profileInput.hairDensity)}
                        </Text>
                      </View>
                    </View>
                  </View>
                ) : null}

                {/* 1. 얼굴형 */}
                <View style={styles.card}>
                  <View style={styles.cardHeaderRow}>
                    <Text style={styles.cardEyebrow}>FACE SHAPE</Text>
                    {analysisDetail.face.source === 'MANUAL' ? (
                      <Text style={styles.sourceTag}>직접 선택</Text>
                    ) : null}
                  </View>

                  <Text style={styles.resultLabel}>
                    {analysisDetail.face.result?.label ?? '미분석'}
                  </Text>

                  {analysisDetail.face.aiResult &&
                  analysisDetail.face.result &&
                  analysisDetail.face.aiResult.key !== analysisDetail.face.result.key ? (
                    <Text style={styles.aiOrigText}>
                      AI 원본: {analysisDetail.face.aiResult.label}
                    </Text>
                  ) : null}

                  {analysisDetail.face.description ? (
                    <Text style={styles.descText}>{analysisDetail.face.description}</Text>
                  ) : null}

                  {/* v1.2 규칙: aiReason은 aiResult 기준 문장이므로 status=ANALYZED일 때만 표시 */}
                  {analysisDetail.face.status === 'ANALYZED' && analysisDetail.face.aiReason ? (
                    <View style={styles.aiReasonBox}>
                      <Text style={styles.aiReasonTitle}>AI 특징 요약</Text>
                      <Text style={styles.aiReasonText}>{analysisDetail.face.aiReason}</Text>
                    </View>
                  ) : null}

                  {analysisDetail.face.confidence !== null ? (
                    <View style={styles.metricBlock}>
                      <View style={styles.metricHeader}>
                        <Text style={styles.metricLabel}>분석 확신도</Text>
                        <Text style={styles.metricValue}>
                          {Math.round(analysisDetail.face.confidence * 100)}%
                        </Text>
                      </View>
                      <View style={styles.progressTrack}>
                        <View
                          style={[
                            styles.progressFill,
                            { width: `${Math.min(100, Math.round(analysisDetail.face.confidence * 100))}%` },
                          ]}
                        />
                      </View>
                    </View>
                  ) : null}

                  {analysisDetail.face.borderline ? (
                    <View style={styles.infoBanner}>
                      <Text style={styles.infoBannerText}>
                        여러 얼굴형의 특징이 함께 보여요. [결과 수정]에서 변경할 수 있어요.
                      </Text>
                    </View>
                  ) : null}

                  {analysisDetail.face.warnings.length > 0 ? (
                    <View style={styles.warningList}>
                      {analysisDetail.face.warnings.map((w, idx) => (
                        <Text key={`${w.code}-${idx}`} style={styles.warningItem}>
                          • {w.message}
                        </Text>
                      ))}
                    </View>
                  ) : null}

                  {analysisDetail.face.candidates.length > 0 ? (
                    <View style={styles.subSection}>
                      <Text style={styles.subTitle}>후보 분포</Text>
                      {analysisDetail.face.candidates.map((c) => (
                        <View key={c.key} style={styles.candRow}>
                          <Text style={styles.candLabel}>{c.label}</Text>
                          <View style={styles.candTrack}>
                            <View
                              style={[
                                styles.candFill,
                                { width: `${Math.min(100, Math.round(c.probability * 100))}%` },
                              ]}
                            />
                          </View>
                          <Text style={styles.candPct}>{Math.round(c.probability * 100)}%</Text>
                        </View>
                      ))}
                    </View>
                  ) : null}

                  {analysisDetail.face.features.length > 0 ? (
                    <View style={styles.subSection}>
                      <Text style={styles.subTitle}>측정 특징</Text>
                      {analysisDetail.face.features.map((f) => (
                        <View key={f.key} style={styles.featureRow}>
                          <View style={{ flex: 1 }}>
                            <Text style={styles.featureLabel}>{f.label}</Text>
                            {f.description ? (
                              <Text style={styles.featureDesc}>{f.description}</Text>
                            ) : null}
                          </View>
                          <Text style={styles.featureVal}>
                            {formatFeatureValue(f.value, f.unit)}
                          </Text>
                        </View>
                      ))}
                    </View>
                  ) : null}
                </View>

                {/* 2. 체형 (Optional 필드는 null/[]일 때 자연스럽게 숨김) */}
                <View style={styles.card}>
                  <View style={styles.cardHeaderRow}>
                    <Text style={styles.cardEyebrow}>BODY TYPE</Text>
                    {analysisDetail.body.source === 'MANUAL' ? (
                      <Text style={styles.sourceTag}>직접 선택</Text>
                    ) : null}
                  </View>

                  <Text style={styles.resultLabel}>
                    {analysisDetail.body.result?.label ?? '미분석'}
                  </Text>

                  {analysisDetail.body.aiResult &&
                  analysisDetail.body.result &&
                  analysisDetail.body.aiResult.key !== analysisDetail.body.result.key ? (
                    <Text style={styles.aiOrigText}>
                      AI 원본: {analysisDetail.body.aiResult.label}
                    </Text>
                  ) : null}

                  {analysisDetail.body.description ? (
                    <Text style={styles.descText}>{analysisDetail.body.description}</Text>
                  ) : null}

                  {analysisDetail.body.confidence !== null ? (
                    <View style={styles.metricBlock}>
                      <View style={styles.metricHeader}>
                        <Text style={styles.metricLabel}>분석 확신도</Text>
                        <Text style={styles.metricValue}>
                          {Math.round(analysisDetail.body.confidence * 100)}%
                        </Text>
                      </View>
                      <View style={styles.progressTrack}>
                        <View
                          style={[
                            styles.progressFill,
                            { width: `${Math.min(100, Math.round(analysisDetail.body.confidence * 100))}%` },
                          ]}
                        />
                      </View>
                    </View>
                  ) : null}

                  {analysisDetail.body.borderline ? (
                    <View style={styles.infoBanner}>
                      <Text style={styles.infoBannerText}>
                        여러 체형의 특징이 함께 보여요. [결과 수정]에서 변경할 수 있어요.
                      </Text>
                    </View>
                  ) : null}

                  {analysisDetail.body.warnings.length > 0 ? (
                    <View style={styles.warningList}>
                      {analysisDetail.body.warnings.map((w, idx) => (
                        <Text key={`${w.code}-${idx}`} style={styles.warningItem}>
                          • {w.message}
                        </Text>
                      ))}
                    </View>
                  ) : null}

                  {analysisDetail.body.candidates.length > 0 ? (
                    <View style={styles.subSection}>
                      <Text style={styles.subTitle}>후보 분포</Text>
                      {analysisDetail.body.candidates.map((c) => (
                        <View key={c.key} style={styles.candRow}>
                          <Text style={styles.candLabel}>{c.label}</Text>
                          <View style={styles.candTrack}>
                            <View
                              style={[
                                styles.candFill,
                                { width: `${Math.min(100, Math.round(c.probability * 100))}%` },
                              ]}
                            />
                          </View>
                          <Text style={styles.candPct}>{Math.round(c.probability * 100)}%</Text>
                        </View>
                      ))}
                    </View>
                  ) : null}

                  {analysisDetail.body.features.length > 0 ? (
                    <View style={styles.subSection}>
                      <Text style={styles.subTitle}>측정 특징</Text>
                      {analysisDetail.body.features.map((f) => (
                        <View key={f.key} style={styles.featureRow}>
                          <View style={{ flex: 1 }}>
                            <Text style={styles.featureLabel}>{f.label}</Text>
                            {f.description ? (
                              <Text style={styles.featureDesc}>{f.description}</Text>
                            ) : null}
                          </View>
                          <Text style={styles.featureVal}>
                            {formatFeatureValue(f.value, f.unit)}
                          </Text>
                        </View>
                      ))}
                    </View>
                  ) : null}

                  {analysisDetail.body.compareToSizeKorea &&
                  analysisDetail.body.compareToSizeKorea.items.length > 0 ? (
                    <View style={styles.subSection}>
                      <Text style={styles.subTitle}>
                        {analysisDetail.body.compareToSizeKorea.referenceLabel} 비교 (
                        {analysisDetail.body.compareToSizeKorea.heightCm}cm 기준)
                      </Text>
                      <View style={styles.axisLegendRow}>
                        <Text style={styles.axisLegendText}>평균보다 작음</Text>
                        <Text style={styles.axisLegendCenter}>평균</Text>
                        <Text style={styles.axisLegendText}>평균보다 큼</Text>
                      </View>
                      {analysisDetail.body.compareToSizeKorea.items.map((item) => {
                        const barWidth = getDivergingBarWidthPercent(item.deviationPercent);
                        return (
                          <View key={item.key} style={styles.sizeChartItem}>
                            <View style={styles.sizeChartHeader}>
                              <Text style={styles.sizeLabel}>{item.label}</Text>
                              <Text style={styles.sizeDir}>{formatSizeKoreaDirection(item)}</Text>
                            </View>
                            <View style={styles.divergingTrack}>
                              <View style={styles.divergingHalfLeft}>
                                {item.direction === 'BELOW' ? (
                                  <View
                                    style={[
                                      styles.divergingBarLeft,
                                      { width: `${barWidth * 2}%` },
                                    ]}
                                  />
                                ) : null}
                              </View>
                              <View style={styles.divergingCenterLine} />
                              <View style={styles.divergingHalfRight}>
                                {item.direction === 'ABOVE' ? (
                                  <View
                                    style={[
                                      styles.divergingBarRight,
                                      { width: `${barWidth * 2}%` },
                                    ]}
                                  />
                                ) : null}
                              </View>
                              {item.direction === 'SIMILAR' ? (
                                <View style={styles.divergingCenterDot} />
                              ) : null}
                            </View>
                            <Text style={styles.sizeMmSub}>
                              추정 {item.userMm}mm · 평균 {item.refMm}mm
                            </Text>
                          </View>
                        );
                      })}
                    </View>
                  ) : null}
                </View>

                <Pressable style={styles.editCtaBtn} onPress={() => setEditMode(true)}>
                  <Text style={styles.editCtaText}>결과 수정하기</Text>
                </Pressable>
              </>
            ) : (
              <View style={styles.card}>
                <Text style={styles.editSectionTitle}>얼굴형</Text>
                <View style={styles.chipWrap}>
                  {analysisDetail.editableOptions.faceShapes.map((opt) => {
                    const active = draftFace === opt.key;
                    return (
                      <Pressable
                        key={opt.key}
                        onPress={() => setDraftFace(opt.key)}
                        style={[styles.chip, active && styles.chipActive]}
                      >
                        <Text style={[styles.chipText, active && styles.chipTextActive]}>
                          {opt.label}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>

                <Text style={[styles.editSectionTitle, { marginTop: 22 }]}>체형</Text>
                <View style={styles.chipWrap}>
                  {analysisDetail.editableOptions.bodyTypes.map((opt) => {
                    const active = draftBody === opt.key;
                    return (
                      <Pressable
                        key={opt.key}
                        onPress={() => setDraftBody(opt.key)}
                        style={[styles.chip, active && styles.chipActive]}
                      >
                        <Text style={[styles.chipText, active && styles.chipTextActive]}>
                          {opt.label}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>

                <Text style={[styles.editSectionTitle, { marginTop: 22 }]}>모발 굵기</Text>
                <View style={styles.chipWrap}>
                  {HAIR_THICKNESS_OPTIONS.map((opt) => {
                    const active = draftThickness === opt.value;
                    return (
                      <Pressable
                        key={opt.value}
                        onPress={() => setDraftThickness(opt.value)}
                        style={[styles.chip, active && styles.chipActive]}
                      >
                        <Text style={[styles.chipText, active && styles.chipTextActive]}>
                          {opt.label}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>

                <Text style={[styles.editSectionTitle, { marginTop: 18 }]}>곱슬 정도</Text>
                <View style={styles.chipWrap}>
                  {HAIR_CURL_OPTIONS.map((opt) => {
                    const active = draftCurl === opt.value;
                    return (
                      <Pressable
                        key={opt.value}
                        onPress={() => setDraftCurl(opt.value)}
                        style={[styles.chip, active && styles.chipActive]}
                      >
                        <Text style={[styles.chipText, active && styles.chipTextActive]}>
                          {opt.label}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>

                <Text style={[styles.editSectionTitle, { marginTop: 18 }]}>머리숱</Text>
                <View style={styles.chipWrap}>
                  {HAIR_DENSITY_OPTIONS.map((opt) => {
                    const active = draftDensity === opt.value;
                    return (
                      <Pressable
                        key={opt.value}
                        onPress={() => setDraftDensity(opt.value)}
                        style={[styles.chip, active && styles.chipActive]}
                      >
                        <Text style={[styles.chipText, active && styles.chipTextActive]}>
                          {opt.label}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>

                <Pressable
                  style={[styles.applyBtn, saving && { opacity: 0.5 }]}
                  onPress={handleSavePatch}
                  disabled={saving}
                >
                  {saving ? (
                    <ActivityIndicator color="#FFFFFF" />
                  ) : (
                    <Text style={styles.applyBtnText}>적용하기</Text>
                  )}
                </Pressable>
              </View>
            )}
          </ScrollView>
        ) : null}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '900',
    color: colors.text,
    letterSpacing: -0.3,
  },
  closeBtn: {
    padding: 4,
  },
  centerBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  loadingText: {
    marginTop: 12,
    fontSize: 13.5,
    color: colors.textSecondary,
  },
  errorText: {
    fontSize: 14,
    color: colors.danger,
    textAlign: 'center',
    marginBottom: 12,
  },
  retryBtn: {
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: radius.sm,
    backgroundColor: colors.primary,
  },
  retryText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  content: {
    padding: 20,
    paddingBottom: 42,
    gap: 16,
  },
  tabRow: {
    flexDirection: 'row',
    backgroundColor: colors.surfaceMuted,
    borderRadius: 10,
    padding: 3,
    gap: 2,
  },
  tabBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
  },
  tabBtnActive: {
    backgroundColor: colors.surface,
    shadowColor: '#000000',
    shadowOpacity: 0.08,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  tabText: {
    fontSize: 13.5,
    fontWeight: '500',
    color: colors.textSecondary,
  },
  tabTextActive: {
    fontWeight: '600',
    color: colors.text,
  },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: 18,
  },
  photoRow: {
    marginTop: 12,
    flexDirection: 'row',
    gap: 10,
  },
  photoSlot: {
    flex: 1,
    gap: 6,
  },
  photoThumb: {
    width: '100%',
    aspectRatio: 3 / 4,
    borderRadius: radius.xs,
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.border,
  },
  photoCaption: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textSecondary,
    textAlign: 'center',
  },
  privacyNote: {
    marginTop: 10,
    fontSize: 11.5,
    color: colors.textMuted,
    lineHeight: 16,
  },
  profileGrid: {
    marginTop: 12,
    flexDirection: 'row',
    gap: 10,
  },
  profileStat: {
    flex: 1,
    backgroundColor: colors.surfaceMuted,
    borderRadius: radius.xs,
    paddingVertical: 11,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  profileStatLabel: {
    fontSize: 12.5,
    fontWeight: '600',
    color: colors.textSecondary,
  },
  profileStatVal: {
    fontSize: 15,
    fontWeight: '900',
    color: colors.text,
  },
  hairSummaryRow: {
    marginTop: 10,
    flexDirection: 'row',
    gap: 8,
  },
  hairTag: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.xs,
    paddingVertical: 9,
    paddingHorizontal: 8,
    alignItems: 'center',
    gap: 3,
  },
  hairTagKey: {
    fontSize: 11,
    fontWeight: '600',
    color: colors.textMuted,
  },
  hairTagVal: {
    fontSize: 13,
    fontWeight: '800',
    color: colors.text,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  cardEyebrow: {
    fontSize: 10.5,
    fontWeight: '800',
    letterSpacing: 1.6,
    color: colors.textMuted,
  },
  sourceTag: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.text,
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: radius.xs,
  },
  resultLabel: {
    marginTop: 6,
    fontSize: 21,
    fontWeight: '900',
    color: colors.text,
  },
  aiOrigText: {
    marginTop: 3,
    fontSize: 12,
    color: colors.textMuted,
  },
  descText: {
    marginTop: 8,
    fontSize: 13.5,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  aiReasonBox: {
    marginTop: 14,
    backgroundColor: colors.accentLight,
    borderRadius: radius.sm,
    padding: 13,
  },
  aiReasonTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.accent, // 진단 요약 포인트 (#3B82F6)
    marginBottom: 4,
  },
  aiReasonText: {
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  metricBlock: {
    marginTop: 14,
  },
  metricHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  metricLabel: {
    fontSize: 12.5,
    fontWeight: '700',
    color: colors.textSecondary,
  },
  metricValue: {
    fontSize: 12.5,
    fontWeight: '800',
    color: colors.accent, // 진단 수치 강조 (#3B82F6)
  },
  progressTrack: {
    height: 5,
    borderRadius: 999,
    backgroundColor: colors.surfaceMuted,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: colors.accent, // 진단 바 강조 (#3B82F6)
  },
  infoBanner: {
    marginTop: 12,
    backgroundColor: colors.surfaceMuted,
    padding: 10,
    borderRadius: radius.xs,
  },
  infoBannerText: {
    fontSize: 12,
    color: colors.textSecondary,
    lineHeight: 18,
  },
  warningList: {
    marginTop: 10,
    gap: 4,
  },
  warningItem: {
    fontSize: 12.5,
    color: colors.textSecondary,
    lineHeight: 18,
  },
  subSection: {
    marginTop: 16,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  subTitle: {
    fontSize: 12.5,
    fontWeight: '800',
    color: colors.text,
    marginBottom: 10,
  },
  candRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
    gap: 8,
  },
  candLabel: {
    width: 80,
    fontSize: 12.5,
    color: colors.textSecondary,
    fontWeight: '600',
  },
  candTrack: {
    flex: 1,
    height: 4,
    backgroundColor: colors.surfaceMuted,
  },
  candFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: colors.accent, // 후보 분포 강조 (#3B82F6)
  },
  candPct: {
    width: 36,
    textAlign: 'right',
    fontSize: 12,
    fontWeight: '700',
    color: colors.text,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 6,
    gap: 10,
  },
  featureLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.text,
  },
  featureDesc: {
    fontSize: 11.5,
    color: colors.textMuted,
    marginTop: 2,
  },
  featureVal: {
    fontSize: 13,
    fontWeight: '800',
    color: colors.text,
  },
  axisLegendRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  axisLegendText: {
    fontSize: 10.5,
    fontWeight: '600',
    color: colors.textMuted,
  },
  axisLegendCenter: {
    fontSize: 10.5,
    fontWeight: '800',
    color: colors.textSecondary,
  },
  sizeChartItem: {
    paddingVertical: 9,
    borderBottomWidth: 1,
    borderBottomColor: colors.surfaceMuted,
    gap: 6,
  },
  sizeChartHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sizeLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.text,
  },
  sizeDir: {
    fontSize: 12.5,
    fontWeight: '800',
    color: colors.text,
  },
  divergingTrack: {
    height: 10,
    backgroundColor: colors.surfaceMuted,
    borderRadius: radius.xs,
    flexDirection: 'row',
    alignItems: 'center',
    position: 'relative',
    overflow: 'hidden',
  },
  divergingHalfLeft: {
    flex: 1,
    height: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-end',
  },
  divergingHalfRight: {
    flex: 1,
    height: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-start',
  },
  divergingCenterLine: {
    width: 1.5,
    height: '100%',
    backgroundColor: colors.borderStrong,
  },
  divergingBarLeft: {
    height: '100%',
    backgroundColor: colors.textMuted,
  },
  divergingBarRight: {
    height: '100%',
    backgroundColor: colors.accent, // Size Korea 편차 강조 (#3B82F6)
  },
  divergingCenterDot: {
    position: 'absolute',
    left: '50%',
    marginLeft: -4,
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.accent,
  },
  sizeMmSub: {
    fontSize: 11,
    color: colors.textMuted,
  },
  editCtaBtn: {
    paddingVertical: 15,
    borderRadius: radius.sm,
    backgroundColor: colors.primary,
    alignItems: 'center',
  },
  editCtaText: {
    fontSize: 14.5,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  editSectionTitle: {
    fontSize: 13,
    fontWeight: '800',
    color: colors.text,
    marginBottom: 10,
  },
  chipWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: radius.full,
    backgroundColor: colors.bg,
  },
  chipActive: {
    backgroundColor: colors.primary,
  },
  chipText: {
    fontSize: 13.5,
    fontWeight: '500',
    color: colors.text,
  },
  chipTextActive: {
    fontWeight: '600',
    color: '#FFFFFF',
  },
  applyBtn: {
    marginTop: 28,
    paddingVertical: 15,
    borderRadius: 14,
    backgroundColor: colors.primary,
    alignItems: 'center',
  },
  applyBtnText: {
    fontSize: 15,
    fontWeight: '800',
    color: '#FFFFFF',
  },
});
