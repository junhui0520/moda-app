// src/api/client.ts — 공통 API 클라이언트 (30초 타임아웃, 에러 표준화, Mock 헤더 지원)

import { Platform } from 'react-native';
import type { ApiError, ApiResponse, ErrorCode, MockHeadersConfig } from './types';

export const DEFAULT_API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL || 'https://moda-app-production.up.railway.app';

const DEFAULT_TIMEOUT_MS = 30_000;

export class ModaApiError extends Error {
  public readonly status: number;
  public readonly code: ErrorCode;
  public readonly details: ApiError['details'];
  public readonly meta: ApiError['meta'];
  public readonly path: string;

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.name = 'ModaApiError';
    this.status = apiError.status;
    this.code = apiError.code;
    this.details = apiError.details ?? [];
    this.meta = apiError.meta ?? null;
    this.path = apiError.path ?? '';
  }

  public isSessionExpired(): boolean {
    return this.code === 'SESSION_NOT_FOUND' || this.code === 'SESSION_EXPIRED';
  }
}

export function isModaApiError(err: unknown): err is ModaApiError {
  return err instanceof ModaApiError;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  formData?: FormData;
  headers?: Record<string, string>;
  timeoutMs?: number;
  baseUrl?: string;
  timeoutCode?: 'AI_TIMEOUT' | 'CHAT_TIMEOUT' | 'INTERNAL_ERROR';
  networkUnavailableCode?: 'AI_SERVER_UNAVAILABLE' | 'CHAT_SERVER_UNAVAILABLE' | 'INTERNAL_ERROR';
}

export function buildMockHeaders(
  mock?: MockHeadersConfig,
  scope: 'face' | 'body' | 'chat' = 'face',
): Record<string, string> {
  if (!mock) return {};
  const headers: Record<string, string> = {};

  if (scope === 'face') {
    if (mock.faceShape) headers['X-Mock-Face-Shape'] = mock.faceShape;
    if (mock.unusable) headers['X-Mock-Unusable'] = 'true';
    if (mock.borderline) headers['X-Mock-Borderline'] = 'true';
  }

  if (scope === 'body') {
    if (mock.bodyType) headers['X-Mock-Body-Type'] = mock.bodyType;
    if (mock.unusable) headers['X-Mock-Unusable'] = 'true';
    if (mock.borderline) headers['X-Mock-Borderline'] = 'true';
    if (mock.bodyMinimal) headers['X-Mock-Body-Minimal'] = 'true';
  }

  if (typeof mock.delayMs === 'number' && mock.delayMs >= 0) {
    headers['X-Mock-Delay-Ms'] = String(Math.min(10000, Math.round(mock.delayMs)));
  }

  return headers;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const {
    method = 'GET',
    body,
    formData,
    headers = {},
    timeoutMs = DEFAULT_TIMEOUT_MS,
    baseUrl = DEFAULT_API_BASE_URL,
    timeoutCode = 'INTERNAL_ERROR',
    networkUnavailableCode = 'INTERNAL_ERROR',
  } = options;

  const cleanBase = baseUrl.replace(/\/+$/, '');
  const url = `${cleanBase}${path}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  const reqHeaders: Record<string, string> = { ...headers };
  let fetchBody: BodyInit | undefined;

  if (formData) {
    // multipart/form-data 전송 시 Content-Type을 직접 지정하지 않아야 boundary가 자동 설정됨
    fetchBody = formData;
  } else if (body !== undefined) {
    reqHeaders['Content-Type'] = 'application/json; charset=UTF-8';
    fetchBody = JSON.stringify(body);
  }

  try {
    const res = await fetch(url, {
      method,
      headers: reqHeaders,
      body: fetchBody,
      signal: controller.signal,
    });

    const text = await res.text();
    let json: ApiResponse<T> | null = null;
    try {
      json = text ? (JSON.parse(text) as ApiResponse<T>) : null;
    } catch {
      json = null;
    }

    if (!json) {
      throw new ModaApiError({
        status: res.status || 500,
        code: res.status === 503 ? networkUnavailableCode : 'INTERNAL_ERROR',
        message: '서버 응답을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
        details: [],
        meta: null,
        path,
        timestamp: new Date().toISOString(),
      });
    }

    if (!json.success) {
      throw new ModaApiError(json.error);
    }

    return json.data;
  } catch (err: unknown) {
    if (err instanceof ModaApiError) {
      throw err;
    }

    const isAbort =
      err instanceof Error && (err.name === 'AbortError' || err.message.includes('aborted'));

    if (isAbort) {
      throw new ModaApiError({
        status: 504,
        code: timeoutCode,
        message:
          timeoutCode === 'CHAT_TIMEOUT'
            ? '답변 생성 시간이 초과되었어요. 다시 시도해 주세요.'
            : timeoutCode === 'AI_TIMEOUT'
              ? '분석 시간이 초과되었어요. 다시 시도해 주세요.'
              : '요청 시간이 초과되었어요. 네트워크 상태를 확인해 주세요.',
        details: [],
        meta: null,
        path,
        timestamp: new Date().toISOString(),
      });
    }

    throw new ModaApiError({
      status: 503,
      code: networkUnavailableCode,
      message:
        networkUnavailableCode === 'CHAT_SERVER_UNAVAILABLE'
          ? '챗봇 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'
          : networkUnavailableCode === 'AI_SERVER_UNAVAILABLE'
            ? '분석 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'
            : '서버에 연결할 수 없어요. 인터넷 연결을 확인해 주세요.',
      details: [],
      meta: null,
      path,
      timestamp: new Date().toISOString(),
    });
  } finally {
    clearTimeout(timer);
  }
}

export async function createImageFormData(uri: string, filename: string): Promise<FormData> {
  const form = new FormData();
  const isPng = uri.toLowerCase().endsWith('.png') || uri.startsWith('data:image/png');
  const mimeType = isPng ? 'image/png' : 'image/jpeg';
  const finalName = isPng ? filename.replace(/\.jpg$/i, '.png') : filename;

  if (Platform.OS === 'web') {
    const response = await fetch(uri);
    const blob = await response.blob();
    const typedBlob = blob.type ? blob : new Blob([blob], { type: mimeType });
    form.append('image', typedBlob, finalName);
  } else {
    form.append('image', {
      uri,
      name: finalName,
      type: mimeType,
    } as unknown as Blob);
  }

  return form;
}
