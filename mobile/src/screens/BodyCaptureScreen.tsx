// src/screens/BodyCaptureScreen.tsx — 풀스크린(스크롤 없음) 전신 촬영 + 5초/3초 카운트다운 + 앨범 선택 + ④ POST /analyze/body

import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Image,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { isModaApiError } from '../api/client';
import { modaApi } from '../api/modaApi';
import type { BodyTypeKey } from '../api/types';
import { LoadingOverlay } from '../components/LoadingOverlay';
import { ManualSelectModal } from '../components/ManualSelectModal';
import type { RootStackParamList } from '../navigation/types';
import { useSessionStore } from '../store/useSessionStore';
import { radius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'BodyCapture'>;
type TimerSec = 0 | 3 | 5;

export function BodyCaptureScreen({ navigation }: Props) {
  const {
    sessionId,
    baseUrl,
    profileInput,
    mockConfig,
    setLocalBodyUri,
    setBodyAnalysis,
    syncAfterPatch,
    resetSessionLocal,
  } = useSessionStore();

  const [permission, requestPermission] = useCameraPermissions();
  const [facing, setFacing] = useState<'back' | 'front'>('back');
  const [timerSec, setTimerSec] = useState<TimerSec>(5);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [previewUri, setPreviewUri] = useState<string | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [manualModalVisible, setManualModalVisible] = useState(false);
  const [manualSaving, setManualSaving] = useState(false);

  const cameraRef = useRef<CameraView | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const clearCountdown = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setCountdown(null);
  };

  useEffect(() => {
    return () => clearCountdown();
  }, []);

  const handleSessionExpired = (message: string) => {
    Alert.alert('세션 만료', message, [
      {
        text: '처음으로',
        onPress: () => {
          resetSessionLocal();
          navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
        },
      },
    ]);
  };

  const uploadAndAnalyze = async (uri: string) => {
    if (!sessionId) {
      handleSessionExpired('세션을 찾을 수 없어요. 처음부터 다시 시작해 주세요.');
      return;
    }

    setPreviewUri(uri);
    setAnalyzing(true);
    try {
      const data = await modaApi.analyzeBody(sessionId, uri, mockConfig, baseUrl);

      if (!data.usable) {
        const msg =
          data.warnings.length > 0
            ? data.warnings.map((w) => w.message).join('\n')
            : '전신이 충분히 보이지 않아요. 머리부터 발끝까지 나오게 다시 시도해 주세요.';
        setPreviewUri(null);
        Alert.alert('다시 촬영해 주세요', msg, [{ text: '다시 시도' }]);
        return;
      }

      setLocalBodyUri(uri);
      setBodyAnalysis(data);

      const infoWarnings = data.warnings.filter((w) => w.severity === 'INFO');
      const goResult = () => navigation.navigate('Result');

      if (infoWarnings.length > 0) {
        Alert.alert('안내', infoWarnings.map((w) => w.message).join('\n'), [
          { text: '결과 보기', onPress: goResult },
        ]);
      } else {
        goResult();
      }
    } catch (err) {
      if (isModaApiError(err)) {
        if (err.isSessionExpired()) {
          handleSessionExpired(err.message);
          return;
        }
        if (err.code === 'PROFILE_INPUT_REQUIRED') {
          Alert.alert('기본 정보 필요', err.message, [
            { text: '입력하기', onPress: () => navigation.navigate('ProfileInput') },
          ]);
          return;
        }
        if (err.code === 'ANALYSIS_IN_PROGRESS') return;
        if (err.code === 'AI_SERVER_UNAVAILABLE' || err.code === 'AI_TIMEOUT') {
          Alert.alert('분석 연결 지연', err.message, [
            { text: '다시 시도', style: 'cancel', onPress: () => setPreviewUri(null) },
            { text: '재시도', onPress: () => uploadAndAnalyze(uri) },
            { text: '직접 선택하기', onPress: () => setManualModalVisible(true) },
          ]);
          return;
        }
      }
      setPreviewUri(null);
      Alert.alert('체형 분석 실패', err instanceof Error ? err.message : '다시 시도해 주세요.');
    } finally {
      setAnalyzing(false);
    }
  };

  const snapNow = async () => {
    if (analyzing || !cameraRef.current) return;
    try {
      const photo = await cameraRef.current.takePictureAsync({
        quality: 0.7,
        exif: false,
      });
      if (photo?.uri) {
        await uploadAndAnalyze(photo.uri);
      }
    } catch {
      Alert.alert('촬영 실패', '카메라 촬영에 실패했어요. 다시 시도해 주세요.');
    }
  };

  const handleShutterPress = () => {
    if (analyzing) return;

    if (countdown !== null) {
      clearCountdown();
      return;
    }

    if (Platform.OS === 'web' || !permission?.granted) {
      handlePickFromAlbum();
      return;
    }

    if (timerSec === 0) {
      snapNow();
      return;
    }

    let remaining = timerSec;
    setCountdown(remaining);
    intervalRef.current = setInterval(() => {
      remaining -= 1;
      if (remaining <= 0) {
        clearCountdown();
        snapNow();
      } else {
        setCountdown(remaining);
      }
    }, 1000);
  };

  const cycleTimer = () => {
    if (countdown !== null) return;
    setTimerSec((prev) => (prev === 5 ? 3 : prev === 3 ? 0 : 5));
  };

  const handlePickFromAlbum = async () => {
    if (analyzing) return;
    clearCountdown();
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted && Platform.OS !== 'web') {
      Alert.alert('권한 필요', '앨범 접근 권한을 허용해 주세요.');
      return;
    }

    const picked = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.7,
      allowsEditing: false,
    });

    if (!picked.canceled && picked.assets[0]?.uri) {
      await uploadAndAnalyze(picked.assets[0].uri);
    }
  };

  const handleManualBodySelect = async (bodyType: BodyTypeKey) => {
    if (!sessionId) return;
    setManualSaving(true);
    try {
      const patched = await modaApi.patchProfile(sessionId, { bodyType }, baseUrl);
      syncAfterPatch({
        progress: patched.progress,
        nextStep: patched.nextStep,
        expiresAt: patched.expiresAt,
        result: patched.result,
      });
      setManualModalVisible(false);
      navigation.navigate('Result');
    } catch (err) {
      if (isModaApiError(err) && err.isSessionExpired()) {
        setManualModalVisible(false);
        handleSessionExpired(err.message);
        return;
      }
      Alert.alert('선택 실패', err instanceof Error ? err.message : '다시 시도해 주세요.');
    } finally {
      setManualSaving(false);
    }
  };

  const canShowCamera = Platform.OS !== 'web' && permission?.granted;

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* 상단 고정 헤더 */}
      <View style={styles.topBar}>
        <Pressable
          onPress={() => {
            clearCountdown();
            navigation.goBack();
          }}
          style={styles.iconBtn}
        >
          <Ionicons name="chevron-back" size={24} color="#FFFFFF" />
        </Pressable>

        <View style={styles.topCenter}>
          <Text style={styles.stepLabel}>
            03 / 03{profileInput?.heightCm ? ` · ${profileInput.heightCm}cm` : ''}
          </Text>
          <Text style={styles.topTitle}>전신 촬영</Text>
        </View>

        <Pressable
          onPress={() => setFacing((prev) => (prev === 'back' ? 'front' : 'back'))}
          style={styles.iconBtn}
          disabled={!canShowCamera}
        >
          <Ionicons
            name="camera-reverse-outline"
            size={22}
            color={canShowCamera ? '#FFFFFF' : 'rgba(255,255,255,0.3)'}
          />
        </Pressable>
      </View>

      {/* 중앙 풀스크린 카메라 뷰 */}
      <View style={styles.cameraArea}>
        {previewUri ? (
          <Image source={{ uri: previewUri }} style={styles.camera} resizeMode="cover" />
        ) : canShowCamera ? (
          <View style={styles.camera}>
            <CameraView ref={cameraRef} style={StyleSheet.absoluteFill} facing={facing} />
            <View pointerEvents="none" style={styles.overlayWrap}>
              <View
                style={[
                  styles.bodyGuide,
                  countdown !== null && styles.bodyGuideActive,
                ]}
              />
              {countdown !== null ? (
                <View style={styles.countdownOverlay}>
                  <Text style={styles.countdownNum}>{countdown}</Text>
                  <Text style={styles.countdownSub}>머리부터 발끝까지 맞춰주세요</Text>
                </View>
              ) : (
                <Text style={styles.guidePill}>머리부터 발끝까지 틀 안에 맞춰주세요</Text>
              )}
            </View>
          </View>
        ) : (
          <View style={styles.permBox}>
            <Ionicons name="body-outline" size={44} color="#A1A1AA" />
            <Text style={styles.permTitle}>
              {Platform.OS === 'web'
                ? '웹에서는 아래 앨범 버튼으로 전신 사진을 선택해 주세요'
                : '카메라 권한이 필요합니다'}
            </Text>
            {Platform.OS !== 'web' ? (
              <Pressable style={styles.permBtn} onPress={requestPermission}>
                <Text style={styles.permBtnText}>카메라 권한 허용</Text>
              </Pressable>
            ) : null}
          </View>
        )}
      </View>

      {/* 하단 고정 촬영 컨트롤 바 */}
      <View style={styles.bottomDock}>
        <Pressable style={styles.sideAction} onPress={handlePickFromAlbum} disabled={analyzing}>
          <Ionicons name="images-outline" size={24} color="#FFFFFF" />
          <Text style={styles.sideActionLabel}>앨범</Text>
        </Pressable>

        <Pressable
          style={[styles.shutterOuter, analyzing && { opacity: 0.4 }]}
          onPress={handleShutterPress}
          disabled={analyzing}
        >
          <View style={[styles.shutterInner, countdown !== null && styles.shutterCancel]}>
            {countdown !== null ? (
              <Ionicons name="close" size={28} color="#FFFFFF" />
            ) : null}
          </View>
        </Pressable>

        <Pressable
          style={styles.sideAction}
          onPress={cycleTimer}
          disabled={analyzing || countdown !== null}
        >
          <Ionicons
            name="timer-outline"
            size={24}
            color={timerSec > 0 ? '#FFFFFF' : '#71717A'}
          />
          <Text style={[styles.sideActionLabel, timerSec === 0 && { color: '#71717A' }]}>
            {timerSec === 0 ? '즉시' : `${timerSec}초`}
          </Text>
        </Pressable>
      </View>

      <LoadingOverlay
        visible={analyzing}
        title="체형과 맞춤 스타일을 분석하고 있어요"
        subtitle="잠시만 기다려 주세요 (최대 30초)"
      />

      <ManualSelectModal
        visible={manualModalVisible}
        mode="body"
        loading={manualSaving}
        onClose={() => setManualModalVisible(false)}
        onSelectBody={handleManualBodySelect}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#09090B',
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  iconBtn: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  topCenter: {
    alignItems: 'center',
  },
  stepLabel: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.8,
    color: '#A1A1AA',
  },
  topTitle: {
    fontSize: 15,
    fontWeight: '800',
    color: '#FFFFFF',
    marginTop: 1,
  },
  cameraArea: {
    flex: 1,
    marginHorizontal: 12,
    borderRadius: radius.md,
    overflow: 'hidden',
    backgroundColor: '#18181B',
  },
  camera: {
    flex: 1,
    width: '100%',
    height: '100%',
  },
  overlayWrap: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.16)',
  },
  bodyGuide: {
    width: '68%',
    height: '82%',
    borderRadius: radius.md,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.88)',
    borderStyle: 'dashed',
  },
  bodyGuideActive: {
    borderColor: '#FFFFFF',
    borderStyle: 'solid',
    borderWidth: 2.5,
  },
  guidePill: {
    position: 'absolute',
    bottom: 20,
    color: '#FFFFFF',
    fontSize: 12.5,
    fontWeight: '700',
    backgroundColor: 'rgba(9, 9, 11, 0.72)',
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: radius.xs,
  },
  countdownOverlay: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  countdownNum: {
    fontSize: 78,
    fontWeight: '900',
    color: '#FFFFFF',
    textShadowColor: 'rgba(0,0,0,0.65)',
    textShadowOffset: { width: 0, height: 2 },
    textShadowRadius: 10,
  },
  countdownSub: {
    marginTop: 6,
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
    backgroundColor: 'rgba(9, 9, 11, 0.72)',
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: radius.xs,
  },
  permBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  permTitle: {
    marginTop: 14,
    fontSize: 14.5,
    fontWeight: '700',
    color: '#E4E4E7',
    textAlign: 'center',
  },
  permBtn: {
    marginTop: 16,
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 18,
    paddingVertical: 11,
    borderRadius: radius.sm,
  },
  permBtnText: {
    color: '#09090B',
    fontWeight: '800',
    fontSize: 13.5,
  },
  bottomDock: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    paddingVertical: 18,
    paddingHorizontal: 24,
  },
  sideAction: {
    width: 60,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  sideActionLabel: {
    fontSize: 11.5,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  shutterOuter: {
    width: 74,
    height: 74,
    borderRadius: 37,
    borderWidth: 3,
    borderColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 4,
  },
  shutterInner: {
    width: '100%',
    height: '100%',
    borderRadius: 32,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutterCancel: {
    backgroundColor: '#DC2626',
  },
});
