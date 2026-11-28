// src/components/MockSettingsModal.tsx — 개발/테스트용 서버 주소 & Mock 헤더 설정 모달

import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { DEFAULT_API_BASE_URL } from '../api/client';
import { modaApi } from '../api/modaApi';
import type { BodyTypeKey, FaceShapeKey } from '../api/types';
import { useSessionStore } from '../store/useSessionStore';
import { colors, radius } from '../theme/theme';

interface MockSettingsModalProps {
  visible: boolean;
  onClose: () => void;
}

const FACE_OPTIONS: { key: FaceShapeKey | ''; label: string }[] = [
  { key: '', label: '기본(oval)' },
  { key: 'diamond', label: '다이아몬드' },
  { key: 'heart', label: '하트형' },
  { key: 'oval', label: '계란형' },
  { key: 'rectangle', label: '직사각형' },
  { key: 'round', label: '둥근형' },
  { key: 'long', label: '긴형' },
];

const BODY_OPTIONS: { key: BodyTypeKey | ''; label: string }[] = [
  { key: '', label: '기본(standard)' },
  { key: 'standard', label: '표준유형' },
  { key: 'small_inv_triangle', label: '작은 역삼각' },
  { key: 'large_rectangle', label: '큰 사각' },
  { key: 'rectangle', label: '사각 체형' },
];

export function MockSettingsModal({ visible, onClose }: MockSettingsModalProps) {
  const { baseUrl, setBaseUrl, mockConfig, setMockConfig, resetMockConfig } = useSessionStore();
  const [urlInput, setUrlInput] = useState(baseUrl);
  const [delayInput, setDelayInput] = useState(
    mockConfig.delayMs !== null && mockConfig.delayMs !== undefined
      ? String(mockConfig.delayMs)
      : '',
  );
  const [healthStatus, setHealthStatus] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  const handleSave = () => {
    setBaseUrl(urlInput);
    const parsedDelay = delayInput.trim() === '' ? null : Number(delayInput);
    setMockConfig({
      delayMs:
        parsedDelay !== null && Number.isFinite(parsedDelay) && parsedDelay >= 0
          ? parsedDelay
          : null,
    });
    onClose();
  };

  const handleHealthCheck = async () => {
    setChecking(true);
    setHealthStatus(null);
    try {
      const target = urlInput.trim() || DEFAULT_API_BASE_URL;
      const data = await modaApi.checkHealth(target);
      setHealthStatus(`연결 성공 (${JSON.stringify(data)})`);
    } catch (err) {
      setHealthStatus(`연결 실패: ${err instanceof Error ? err.message : '알 수 없는 오류'}`);
    } finally {
      setChecking(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={styles.root}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>테스트 & Mock 서버 설정</Text>
          <Pressable onPress={onClose} style={styles.closeBtn}>
            <Text style={styles.closeText}>닫기</Text>
          </Pressable>
        </View>

        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.sectionLabel}>API Base URL</Text>
          <TextInput
            value={urlInput}
            onChangeText={setUrlInput}
            style={styles.input}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder={DEFAULT_API_BASE_URL}
          />
          <View style={styles.urlQuickRow}>
            <Pressable
              style={styles.quickBtn}
              onPress={() => setUrlInput(DEFAULT_API_BASE_URL)}
            >
              <Text style={styles.quickBtnText}>Railway 배포 서버</Text>
            </Pressable>
            <Pressable
              style={styles.quickBtn}
              onPress={() => setUrlInput('http://localhost:8080')}
            >
              <Text style={styles.quickBtnText}>localhost:8080</Text>
            </Pressable>
            <Pressable style={styles.healthBtn} onPress={handleHealthCheck} disabled={checking}>
              {checking ? (
                <ActivityIndicator size="small" color="#FFFFFF" />
              ) : (
                <Text style={styles.healthBtnText}>연결 테스트</Text>
              )}
            </Pressable>
          </View>
          {healthStatus ? <Text style={styles.healthText}>{healthStatus}</Text> : null}

          <Text style={[styles.sectionLabel, { marginTop: 20 }]}>X-Mock-Face-Shape</Text>
          <View style={styles.chipWrap}>
            {FACE_OPTIONS.map((opt) => {
              const selected = (mockConfig.faceShape || '') === opt.key;
              return (
                <Pressable
                  key={opt.label}
                  onPress={() => setMockConfig({ faceShape: opt.key })}
                  style={[styles.chip, selected && styles.chipSelected]}
                >
                  <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
                    {opt.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <Text style={[styles.sectionLabel, { marginTop: 16 }]}>X-Mock-Body-Type</Text>
          <View style={styles.chipWrap}>
            {BODY_OPTIONS.map((opt) => {
              const selected = (mockConfig.bodyType || '') === opt.key;
              return (
                <Pressable
                  key={opt.label}
                  onPress={() => setMockConfig({ bodyType: opt.key })}
                  style={[styles.chip, selected && styles.chipSelected]}
                >
                  <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
                    {opt.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.switchTitle}>X-Mock-Unusable</Text>
              <Text style={styles.switchSub}>재촬영 경고(usable: false) 강제 테스트</Text>
            </View>
            <Switch
              value={Boolean(mockConfig.unusable)}
              onValueChange={(v) => setMockConfig({ unusable: v })}
            />
          </View>

          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.switchTitle}>X-Mock-Borderline</Text>
              <Text style={styles.switchSub}>경계값 안내 경고(INFO) 테스트</Text>
            </View>
            <Switch
              value={Boolean(mockConfig.borderline)}
              onValueChange={(v) => setMockConfig({ borderline: v })}
            />
          </View>

          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.switchTitle}>X-Mock-Body-Minimal</Text>
              <Text style={styles.switchSub}>체형 상세 최소 필드(null / []) 테스트</Text>
            </View>
            <Switch
              value={Boolean(mockConfig.bodyMinimal)}
              onValueChange={(v) => setMockConfig({ bodyMinimal: v })}
            />
          </View>

          <Text style={[styles.sectionLabel, { marginTop: 16 }]}>X-Mock-Delay-Ms (0~10000)</Text>
          <TextInput
            value={delayInput}
            onChangeText={setDelayInput}
            keyboardType="number-pad"
            style={styles.input}
            placeholder="비워두면 서버 기본값(800ms)"
          />

          <View style={styles.footerRow}>
            <Pressable
              style={styles.resetBtn}
              onPress={() => {
                resetMockConfig();
                setDelayInput('');
              }}
            >
              <Text style={styles.resetBtnText}>Mock 초기화</Text>
            </Pressable>
            <Pressable style={styles.saveBtn} onPress={handleSave}>
              <Text style={styles.saveBtnText}>저장</Text>
            </Pressable>
          </View>
        </ScrollView>
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
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '800',
    color: colors.text,
  },
  closeBtn: {
    padding: 4,
  },
  closeText: {
    fontSize: 15,
    fontWeight: '700',
    color: colors.textSecondary,
  },
  content: {
    padding: 20,
    paddingBottom: 40,
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.text,
    marginBottom: 8,
  },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    paddingVertical: 11,
    fontSize: 14,
    color: colors.text,
  },
  urlQuickRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 8,
  },
  quickBtn: {
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: radius.sm,
  },
  quickBtnText: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.textSecondary,
  },
  healthBtn: {
    backgroundColor: colors.accent,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: radius.sm,
  },
  healthBtnText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  healthText: {
    marginTop: 8,
    fontSize: 12,
    color: colors.textSecondary,
  },
  chipWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: radius.full,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderStrong,
  },
  chipSelected: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  chipText: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textSecondary,
  },
  chipTextSelected: {
    color: '#FFFFFF',
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 16,
    backgroundColor: colors.surface,
    padding: 14,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  switchTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.text,
  },
  switchSub: {
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 2,
  },
  footerRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 24,
  },
  resetBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
    alignItems: 'center',
  },
  resetBtnText: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textSecondary,
  },
  saveBtn: {
    flex: 2,
    paddingVertical: 14,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: 'center',
  },
  saveBtnText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
  },
});
