import Constants from 'expo-constants';
import { Platform } from 'react-native';
import type { ApiError, ApiResponse } from './types';

// 서버 주소: EXPO_PUBLIC_API_URL > Expo 개발 서버가 떠 있는 PC의 IP(실기기·에뮬레이터 자동) > localhost
// 개발 서버 호스트는 PC IP라 `npx expo start` 한 PC에서 백엔드도 돌린다는 가정. 다른 PC면 EXPO_PUBLIC_API_URL 지정
// 웹은 페이지를 연 주소 그대로 (PC 브라우저 = localhost, 폰 브라우저 = PC IP)
const devHost = Platform.OS === 'web' ? window.location.hostname : Constants.expoConfig?.hostUri?.split(':')[0];
export const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? `http://${devHost ?? 'localhost'}:8080`;
const TIMEOUT_MS = 30_000;

export class ApiException extends Error {
  constructor(public readonly error: ApiError) {
    super(error.message);
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(`${BASE_URL}/api/v1${path}`, { ...init, signal: controller.signal });
    const body = (await res.json()) as ApiResponse<T>;
    if (!body.success) throw new ApiException(body.error);
    return body.data;
  } catch (e) {
    if (e instanceof ApiException) throw e;
    if (controller.signal.aborted) throw new Error('서버 응답 시간이 초과되었어요.');
    // 원인(네트워크 실패인지, 응답 파싱 실패인지)을 같이 보여 줘야 실기기에서 진단 가능
    throw new Error(`서버(${BASE_URL})에 연결할 수 없어요. [${path}] ${(e as Error).message}`);
  } finally {
    clearTimeout(timer);
  }
}

export const jsonBody = (method: 'POST' | 'PATCH', body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});
