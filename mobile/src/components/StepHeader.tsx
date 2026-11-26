// src/components/StepHeader.tsx — 에디토리얼 미니멀 단계 헤더

import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme/theme';

interface StepHeaderProps {
  currentStep: 1 | 2 | 3;
  title: string;
  subtitle?: string;
}

export function StepHeader({ currentStep, title, subtitle }: StepHeaderProps) {
  return (
    <View style={styles.container}>
      <View style={styles.progressRow}>
        {[1, 2, 3].map((step) => (
          <View
            key={step}
            style={[
              styles.bar,
              step <= currentStep ? styles.barActive : styles.barInactive,
            ]}
          />
        ))}
        <Text style={styles.stepText}>0{currentStep} / 03</Text>
      </View>
      <Text style={styles.title}>{title}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 16,
  },
  bar: {
    flex: 1,
    height: 2,
  },
  barActive: {
    backgroundColor: colors.primary,
  },
  barInactive: {
    backgroundColor: colors.border,
  },
  stepText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    color: colors.textMuted,
    marginLeft: 6,
  },
  title: {
    fontSize: 24,
    fontWeight: '900',
    color: colors.text,
    letterSpacing: -0.6,
    lineHeight: 31,
  },
  subtitle: {
    marginTop: 6,
    fontSize: 13.5,
    color: colors.textSecondary,
    lineHeight: 20,
  },
});
