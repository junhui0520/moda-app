// src/components/LiveAnalysisView.tsx — AI 서버의 실시간 측정 페이지를 띄우고 측정값을 받는 화면
//
// 복사 위치: mobile/src/components/LiveAnalysisView.tsx
// 필요 패키지: npx.cmd expo install react-native-webview
// 환경변수:   EXPO_PUBLIC_AI_LIVE_URL=https://<AI 서버 주소>/live/live.html
// 사용법은 ai/LIVE_INTEGRATION.md 3장 참고.

import React, { useEffect } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import type { LiveMeasurement } from '../api/types';

export const AI_LIVE_PAGE_URL =
  process.env.EXPO_PUBLIC_AI_LIVE_URL || 'https://moda-ai-production.up.railway.app/live/live.html';

export type LiveMode = 'face' | 'body';
export type LiveStatus = 'loading' | 'ready' | 'measuring' | 'between' | 'error';

type Props = {
  mode: LiveMode;
  /** 측정 완료 — payload 를 그대로 modaApi.analyzeFaceLive / analyzeBodyLive 에 넘기면 됨 */
  onResult: (payload: LiveMeasurement) => void;
  /** 카메라 권한 거부·모델 로딩 실패 등 → 기존 사진 촬영으로 대체할 때 사용 */
  onError?: (code: string, message?: string) => void;
  onStatus?: (status: LiveStatus) => void;
  /** 값을 바꾸면 페이지를 새로 열어 처음부터 다시 측정 */
  reloadKey?: number;
  /** 'environment' 로 주면 후면 카메라 */
  camera?: 'user' | 'environment';
};

type LiveMessage =
  | { type: 'moda-live-result'; mode: LiveMode; payload: LiveMeasurement }
  | { type: 'moda-live-status'; mode: LiveMode; status: LiveStatus; code?: string; message?: string };

function parse(raw: unknown): LiveMessage | null {
  let msg: unknown = raw;
  if (typeof raw === 'string') {
    try {
      msg = JSON.parse(raw);
    } catch {
      return null;
    }
  }
  if (!msg || typeof msg !== 'object') return null;
  const t = (msg as { type?: unknown }).type;
  return t === 'moda-live-result' || t === 'moda-live-status' ? (msg as LiveMessage) : null;
}

export function LiveAnalysisView({ mode, onResult, onError, onStatus, reloadKey = 0, camera = 'user' }: Props) {
  const uri = `${AI_LIVE_PAGE_URL}?mode=${mode}${camera === 'environment' ? '&camera=environment' : ''}`;

  const handle = (raw: unknown) => {
    const msg = parse(raw);
    if (!msg || msg.mode !== mode) return;
    if (msg.type === 'moda-live-result') {
      onResult(msg.payload);
    } else {
      onStatus?.(msg.status);
      if (msg.status === 'error') onError?.(msg.code ?? 'LOAD_FAILED', msg.message);
    }
  };

  // 웹(Expo web): iframe 이 보내는 window 메시지 수신
  useEffect(() => {
    if (Platform.OS !== 'web') return undefined;
    const listener = (e: MessageEvent) => handle(e.data);
    window.addEventListener('message', listener);
    return () => window.removeEventListener('message', listener);
    // handle 은 렌더마다 새로 만들어지지만 mode·콜백이 바뀔 때만 다시 등록하면 충분
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, onResult, onError, onStatus]);

  if (Platform.OS === 'web') {
    return (
      <View style={styles.fill}>
        <iframe key={reloadKey} src={uri} allow="camera" style={{ border: 0, width: '100%', height: '100%' }} />
      </View>
    );
  }

  return (
    <WebView
      key={reloadKey}
      style={styles.fill}
      source={{ uri }}
      onMessage={(e: WebViewMessageEvent) => handle(e.nativeEvent.data)}
      // iOS: 카메라 영상을 페이지 안에서 재생 + 앱이 가진 카메라 권한을 페이지에 그대로 허용
      allowsInlineMediaPlayback
      mediaPlaybackRequiresUserAction={false}
      mediaCapturePermissionGrantType="grant"
      javaScriptEnabled
      domStorageEnabled
      bounces={false}
      scrollEnabled={false}
      onError={(e) => onError?.('LOAD_FAILED', e.nativeEvent.description)}
      onHttpError={(e) => onError?.('LOAD_FAILED', `HTTP ${e.nativeEvent.statusCode}`)}
    />
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: '#09090B' },
});
