// src/screens/ProfileInputScreen.tsx — A안 에디토리얼 기본 정보 입력 화면 (② POST /profile-input)

import React, { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { isModaApiError } from '../api/client';
import { modaApi } from '../api/modaApi';
import type { HairCurl, HairDensity, HairThickness } from '../api/types';
import { StepHeader } from '../components/StepHeader';
import type { RootStackParamList } from '../navigation/types';
import { useSessionStore } from '../store/useSessionStore';
import { colors, radius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'ProfileInput'>;

const THICKNESS_OPTIONS: { value: HairThickness; label: string }[] = [
  { value: 'THIN', label: '가는 편' },
  { value: 'MEDIUM', label: '보통' },
  { value: 'THICK', label: '굵은 편' },
];
const CURL_OPTIONS: { value: HairCurl; label: string }[] = [
  { value: 'STRAIGHT', label: '직모' },
  { value: 'SEMI_CURLY', label: '반곱슬' },
  { value: 'CURLY', label: '곱슬' },
];
const DENSITY_OPTIONS: { value: HairDensity; label: string }[] = [
  { value: 'LOW', label: '적은 편' },
  { value: 'MEDIUM', label: '보통' },
  { value: 'HIGH', label: '많은 편' },
];

export function ProfileInputScreen({ navigation }: Props) {
  const { sessionId, baseUrl, profileInput, setProfileState, resetSessionLocal } =
    useSessionStore();

  const [age, setAge] = useState(profileInput ? String(profileInput.age) : '');
  const [heightCm, setHeightCm] = useState(profileInput ? String(profileInput.heightCm) : '');
  const [weightKg, setWeightKg] = useState(
    profileInput?.weightKg != null ? String(profileInput.weightKg) : '',
  );
  const [hairThickness, setHairThickness] = useState<HairThickness>(
    profileInput?.hairThickness ?? 'MEDIUM',
  );
  const [hairCurl, setHairCurl] = useState<HairCurl>(profileInput?.hairCurl ?? 'STRAIGHT');
  const [hairDensity, setHairDensity] = useState<HairDensity>(
    profileInput?.hairDensity ?? 'MEDIUM',
  );

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!sessionId) {
      resetSessionLocal();
      navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
      return;
    }

    const errors: Record<string, string> = {};
    const parsedAge = Number(age.trim());
    const parsedHeight = Number(heightCm.trim());
    const trimmedWeight = weightKg.trim();
    const parsedWeight = trimmedWeight === '' ? null : Number(trimmedWeight);

    if (!age.trim() || !Number.isInteger(parsedAge) || parsedAge < 14 || parsedAge > 80) {
      errors.age = '14~80 사이 정수';
    }
    if (
      !heightCm.trim() ||
      !Number.isInteger(parsedHeight) ||
      parsedHeight < 140 ||
      parsedHeight > 210
    ) {
      errors.heightCm = '140~210 사이 정수';
    }
    if (
      parsedWeight !== null &&
      (!Number.isInteger(parsedWeight) || parsedWeight < 30 || parsedWeight > 200)
    ) {
      errors.weightKg = '30~200 사이 정수 또는 비워두기';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setLoading(true);

    try {
      const data = await modaApi.submitProfileInput(
        sessionId,
        {
          age: parsedAge,
          heightCm: parsedHeight,
          weightKg: parsedWeight,
          hairThickness,
          hairCurl,
          hairDensity,
        },
        baseUrl,
      );

      setProfileState({
        profileInput: data.profileInput,
        progress: data.progress,
        nextStep: data.nextStep,
        expiresAt: data.expiresAt,
        bodyAnalysisReset: data.bodyAnalysisReset,
      });

      const goNext = () => {
        if (data.nextStep === 'RESULT_READY') {
          navigation.navigate('Result');
        } else if (data.nextStep === 'BODY_ANALYSIS') {
          navigation.navigate('BodyCapture');
        } else {
          navigation.navigate('FaceCapture');
        }
      };

      if (data.bodyAnalysisReset) {
        Alert.alert(
          '전신 재분석 필요',
          '키가 변경되어 전신 사진을 다시 분석해야 해요.',
          [{ text: '확인', onPress: goNext }],
        );
      } else {
        goNext();
      }
    } catch (err) {
      if (isModaApiError(err)) {
        if (err.isSessionExpired()) {
          Alert.alert('세션 만료', err.message, [
            {
              text: '처음으로',
              onPress: () => {
                resetSessionLocal();
                navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
              },
            },
          ]);
          return;
        }
        if (err.code === 'INVALID_REQUEST' && err.details.length > 0) {
          const serverErrors: Record<string, string> = {};
          for (const d of err.details) {
            serverErrors[d.field] = d.reason;
          }
          setFieldErrors(serverErrors);
          return;
        }
      }
      Alert.alert(
        '저장 실패',
        err instanceof Error ? err.message : '잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
          <StepHeader currentStep={1} title="기본 정보" />

          {/* 나이 · 키 2열 배치 */}
          <View style={styles.row2}>
            <View style={{ flex: 1 }}>
              <Text style={styles.label}>나이 *</Text>
              <View style={[styles.inputBox, fieldErrors.age ? styles.inputError : null]}>
                <TextInput
                  value={age}
                  onChangeText={setAge}
                  keyboardType="number-pad"
                  placeholder="23"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  maxLength={2}
                />
                <Text style={styles.unit}>세</Text>
              </View>
              {fieldErrors.age ? <Text style={styles.errorText}>{fieldErrors.age}</Text> : null}
            </View>

            <View style={{ flex: 1 }}>
              <Text style={styles.label}>키 *</Text>
              <View style={[styles.inputBox, fieldErrors.heightCm ? styles.inputError : null]}>
                <TextInput
                  value={heightCm}
                  onChangeText={setHeightCm}
                  keyboardType="number-pad"
                  placeholder="175"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  maxLength={3}
                />
                <Text style={styles.unit}>cm</Text>
              </View>
              {fieldErrors.heightCm ? (
                <Text style={styles.errorText}>{fieldErrors.heightCm}</Text>
              ) : null}
            </View>
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>
              몸무게 <Text style={styles.optional}>(선택 · 화면 미표시)</Text>
            </Text>
            <View style={[styles.inputBox, fieldErrors.weightKg ? styles.inputError : null]}>
              <TextInput
                value={weightKg}
                onChangeText={setWeightKg}
                keyboardType="number-pad"
                placeholder="비워두셔도 됩니다"
                placeholderTextColor={colors.textMuted}
                style={styles.input}
                maxLength={3}
              />
              <Text style={styles.unit}>kg</Text>
            </View>
            {fieldErrors.weightKg ? (
              <Text style={styles.errorText}>{fieldErrors.weightKg}</Text>
            ) : null}
          </View>

          <View style={styles.divider} />

          <Text style={styles.sectionEyebrow}>HAIR PROFILE</Text>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>모발 굵기</Text>
            <View style={styles.segmentRow}>
              {THICKNESS_OPTIONS.map((opt) => {
                const active = hairThickness === opt.value;
                return (
                  <Pressable
                    key={opt.value}
                    style={[styles.segmentBtn, active && styles.segmentBtnActive]}
                    onPress={() => setHairThickness(opt.value)}
                  >
                    <Text style={[styles.segmentText, active && styles.segmentTextActive]}>
                      {opt.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>곱슬 정도</Text>
            <View style={styles.segmentRow}>
              {CURL_OPTIONS.map((opt) => {
                const active = hairCurl === opt.value;
                return (
                  <Pressable
                    key={opt.value}
                    style={[styles.segmentBtn, active && styles.segmentBtnActive]}
                    onPress={() => setHairCurl(opt.value)}
                  >
                    <Text style={[styles.segmentText, active && styles.segmentTextActive]}>
                      {opt.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.label}>머리숱</Text>
            <View style={styles.segmentRow}>
              {DENSITY_OPTIONS.map((opt) => {
                const active = hairDensity === opt.value;
                return (
                  <Pressable
                    key={opt.value}
                    style={[styles.segmentBtn, active && styles.segmentBtnActive]}
                    onPress={() => setHairDensity(opt.value)}
                  >
                    <Text style={[styles.segmentText, active && styles.segmentTextActive]}>
                      {opt.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        </ScrollView>

        <View style={styles.bottomBar}>
          <Pressable
            style={[styles.nextBtn, loading && { opacity: 0.5 }]}
            onPress={handleSubmit}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.nextBtnText}>다음 · 얼굴 촬영</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  container: {
    paddingHorizontal: 22,
    paddingTop: 12,
    paddingBottom: 24,
  },
  row2: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
  },
  fieldGroup: {
    marginBottom: 16,
  },
  label: {
    fontSize: 12.5,
    fontWeight: '800',
    color: colors.text,
    marginBottom: 7,
  },
  optional: {
    fontWeight: '500',
    color: colors.textMuted,
  },
  inputBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    paddingHorizontal: 13,
  },
  inputError: {
    borderColor: colors.danger,
  },
  input: {
    flex: 1,
    paddingVertical: 12,
    fontSize: 15,
    fontWeight: '700',
    color: colors.text,
  },
  unit: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textMuted,
    marginLeft: 6,
  },
  errorText: {
    marginTop: 4,
    fontSize: 11.5,
    color: colors.danger,
  },
  divider: {
    height: 1,
    backgroundColor: colors.border,
    marginVertical: 14,
  },
  sectionEyebrow: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    color: colors.textMuted,
    marginBottom: 14,
  },
  segmentRow: {
    flexDirection: 'row',
    gap: 8,
  },
  segmentBtn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: 'center',
  },
  segmentBtnActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primary,
  },
  segmentText: {
    fontSize: 13.5,
    fontWeight: '700',
    color: colors.textSecondary,
  },
  segmentTextActive: {
    color: '#FFFFFF',
  },
  bottomBar: {
    paddingHorizontal: 22,
    paddingTop: 10,
    paddingBottom: 16,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.bg,
  },
  nextBtn: {
    backgroundColor: colors.primary,
    paddingVertical: 16,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  nextBtnText: {
    color: '#FFFFFF',
    fontSize: 15.5,
    fontWeight: '800',
  },
});
