// src/components/LoadingOverlay.tsx — 모노크롬 에디토리얼 분석 로딩 오버레이

import React from 'react';
import { ActivityIndicator, Modal, StyleSheet, Text, View } from 'react-native';
import { colors, radius } from '../theme/theme';

interface LoadingOverlayProps {
  visible: boolean;
  title?: string;
  subtitle?: string;
}

export function LoadingOverlay({
  visible,
  title = 'AI가 분석하고 있어요',
  subtitle = '잠시만 기다려 주세요 (최대 30초)',
}: LoadingOverlayProps) {
  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(9, 9, 11, 0.72)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 290,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 30,
    paddingHorizontal: 22,
    alignItems: 'center',
  },
  title: {
    marginTop: 18,
    fontSize: 16,
    fontWeight: '800',
    color: colors.text,
    textAlign: 'center',
    letterSpacing: -0.3,
  },
  subtitle: {
    marginTop: 6,
    fontSize: 12.5,
    color: colors.textSecondary,
    textAlign: 'center',
  },
});
