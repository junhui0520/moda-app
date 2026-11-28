// src/components/ManualSelectModal.tsx — AI 서버 장애(503/504) 시에만 노출되는 직접 선택 모달

import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { BodyTypeKey, FaceShapeKey } from '../api/types';
import { colors, radius } from '../theme/theme';

interface ManualSelectModalProps {
  visible: boolean;
  mode: 'face' | 'body';
  loading?: boolean;
  onClose: () => void;
  onSelectFace?: (faceShape: FaceShapeKey) => void;
  onSelectBody?: (bodyType: BodyTypeKey) => void;
}

const FACE_ITEMS: { key: FaceShapeKey; title: string; desc: string }[] = [
  { key: 'oval', title: '계란형', desc: '이마·광대·턱 비율이 고르게 이어지는 형태' },
  { key: 'diamond', title: '다이아몬드형', desc: '광대 라인이 또렷하고 이마와 턱이 갸름한 형태' },
  { key: 'heart', title: '하트형', desc: '이마 라인이 시원하고 턱선이 갸름하게 모이는 형태' },
  { key: 'rectangle', title: '직사각형', desc: '세로 비율이 시원하고 턱선이 또렷한 형태' },
  { key: 'round', title: '둥근형', desc: '부드러운 곡선으로 이어지는 친근한 윤곽' },
  { key: 'long', title: '긴형', desc: '세로 라인이 시원하게 뻗은 차분한 윤곽' },
];

const BODY_ITEMS: { key: BodyTypeKey; title: string; desc: string }[] = [
  { key: 'standard', title: '표준유형', desc: '어깨·허리·엉덩이 비율이 고르게 균형 잡힌 체형' },
  { key: 'small_inv_triangle', title: '작은 역삼각형', desc: '몸통이 슬림하고 어깨선이 또렷한 V라인 체형' },
  { key: 'large_rectangle', title: '큰 사각 체형', desc: '어깨부터 허리까지 탄탄하고 듬직한 프레임' },
  { key: 'rectangle', title: '사각 체형', desc: '어깨·허리·엉덩이 너비가 곧게 이어지는 체형' },
];

export function ManualSelectModal({
  visible,
  mode,
  loading = false,
  onClose,
  onSelectFace,
  onSelectBody,
}: ManualSelectModalProps) {
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const items = mode === 'face' ? FACE_ITEMS : BODY_ITEMS;

  const handleConfirm = () => {
    if (!selectedKey) return;
    if (mode === 'face' && onSelectFace) {
      onSelectFace(selectedKey as FaceShapeKey);
    } else if (mode === 'body' && onSelectBody) {
      onSelectBody(selectedKey as BodyTypeKey);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View style={styles.sheet}>
          <Text style={styles.title}>
            {mode === 'face' ? '얼굴형 직접 선택' : '체형 직접 선택'}
          </Text>
          <Text style={styles.subtitle}>
            일시적으로 AI 분석 연결이 원활하지 않아 직접 선택으로 진행할 수 있어요. 결과 화면의 [분석 상세보기]에서 언제든 바꿀 수 있습니다.
          </Text>

          <ScrollView style={styles.list} contentContainerStyle={{ gap: 10 }}>
            {items.map((item) => {
              const active = selectedKey === item.key;
              return (
                <Pressable
                  key={item.key}
                  onPress={() => setSelectedKey(item.key)}
                  style={[styles.optionCard, active && styles.optionCardActive]}
                >
                  <Text style={[styles.optionTitle, active && styles.optionTitleActive]}>
                    {item.title}
                  </Text>
                  <Text style={styles.optionDesc}>{item.desc}</Text>
                </Pressable>
              );
            })}
          </ScrollView>

          <View style={styles.btnRow}>
            <Pressable style={styles.cancelBtn} onPress={onClose} disabled={loading}>
              <Text style={styles.cancelText}>취소</Text>
            </Pressable>
            <Pressable
              style={[styles.confirmBtn, (!selectedKey || loading) && styles.disabledBtn]}
              onPress={handleConfirm}
              disabled={!selectedKey || loading}
            >
              {loading ? (
                <ActivityIndicator color="#FFFFFF" />
              ) : (
                <Text style={styles.confirmText}>선택 완료</Text>
              )}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(17, 24, 39, 0.55)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    padding: 20,
    maxHeight: '82%',
  },
  title: {
    fontSize: 19,
    fontWeight: '800',
    color: colors.text,
  },
  subtitle: {
    marginTop: 6,
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  list: {
    marginTop: 16,
    maxHeight: 360,
  },
  optionCard: {
    padding: 14,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.bg,
  },
  optionCardActive: {
    borderColor: colors.accent,
    backgroundColor: colors.accentLight,
  },
  optionTitle: {
    fontSize: 15,
    fontWeight: '800',
    color: colors.text,
  },
  optionTitleActive: {
    color: colors.accent,
  },
  optionDesc: {
    marginTop: 3,
    fontSize: 12.5,
    color: colors.textSecondary,
  },
  btnRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 18,
  },
  cancelBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceMuted,
    alignItems: 'center',
  },
  cancelText: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textSecondary,
  },
  confirmBtn: {
    flex: 2,
    paddingVertical: 14,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: 'center',
  },
  disabledBtn: {
    opacity: 0.45,
  },
  confirmText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
  },
});
