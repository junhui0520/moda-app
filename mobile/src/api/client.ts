import type { ApiError, ApiResponse } from './types';

// 실기기·Android 에뮬레이터는 localhost가 PC가 아니므로 EXPO_PUBLIC_API_URL로 지정 (mobile/README.md)
export const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';
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
    throw new Error(controller.signal.aborted ? '서버 응답 시간이 초과되었어요.' : `서버(${BASE_URL})에 연결할 수 없어요.`);
  } finally {
    clearTimeout(timer);
  }
}

export const jsonBody = (method: 'POST' | 'PATCH', body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});
