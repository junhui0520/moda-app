// src/api/modaApi.ts — docs/api-spec.md v1.2 API 10종 + 헬스체크 호출 함수

import { apiRequest, buildMockHeaders, createImageFormData } from './client';
import type {
  AnalysisDetailData,
  BodyAnalysisData,
  ChatHistoryData,
  ChatSendData,
  ChatSendRequest,
  FaceAnalysisData,
  GuestSessionData,
  GuestSessionRequest,
  MockHeadersConfig,
  ProfileInputData,
  ProfileInputRequest,
  ProfilePatchData,
  ProfilePatchRequest,
  ResultData,
  SessionDeleteData,
  StyleImages,
} from './types';

export const modaApi = {
  // 스타일 이미지 경로 (imageKey → 큰 사진 + 아이템 사진)
  getStyleImages: (baseUrl?: string) =>
    apiRequest<Record<string, StyleImages[]>>('/api/v1/style-images', { method: 'GET', baseUrl, timeoutMs: 10_000 }),

  // 헬스체크
  checkHealth: (baseUrl?: string) =>
    apiRequest<{ status: string; aiMode?: string; chatMode?: string }>('/api/v1/health', {
      method: 'GET',
      baseUrl,
      timeoutMs: 10_000,
    }),

  // ① 게스트 세션 생성
  createGuestSession: (req: GuestSessionRequest, baseUrl?: string) =>
    apiRequest<GuestSessionData>('/api/v1/sessions/guest', {
      method: 'POST',
      body: req,
      baseUrl,
    }),

  // ② 기본 정보 입력
  submitProfileInput: (sessionId: string, req: ProfileInputRequest, baseUrl?: string) =>
    apiRequest<ProfileInputData>(`/api/v1/sessions/${sessionId}/profile-input`, {
      method: 'POST',
      body: req,
      baseUrl,
    }),

  // ③ 얼굴 사진 분석
  analyzeFace: async (
    sessionId: string,
    photoUri: string,
    mock?: MockHeadersConfig,
    baseUrl?: string,
  ) => {
    const formData = await createImageFormData(photoUri, 'face.jpg');
    return apiRequest<FaceAnalysisData>(`/api/v1/sessions/${sessionId}/analyze/face`, {
      method: 'POST',
      formData,
      headers: buildMockHeaders(mock, 'face'),
      baseUrl,
      timeoutMs: 30_000,
      timeoutCode: 'AI_TIMEOUT',
      networkUnavailableCode: 'AI_SERVER_UNAVAILABLE',
    });
  },

  // ④ 전신 사진 분석
  analyzeBody: async (
    sessionId: string,
    photoUri: string,
    mock?: MockHeadersConfig,
    baseUrl?: string,
  ) => {
    const formData = await createImageFormData(photoUri, 'body.jpg');
    return apiRequest<BodyAnalysisData>(`/api/v1/sessions/${sessionId}/analyze/body`, {
      method: 'POST',
      formData,
      headers: buildMockHeaders(mock, 'body'),
      baseUrl,
      timeoutMs: 30_000,
      timeoutCode: 'AI_TIMEOUT',
      networkUnavailableCode: 'AI_SERVER_UNAVAILABLE',
    });
  },

  // ⑤ 결과 조회 (유형 + 헤어 2종 + 패션 3종)
  getResult: (sessionId: string, baseUrl?: string) =>
    apiRequest<ResultData>(`/api/v1/sessions/${sessionId}/result`, {
      method: 'GET',
      baseUrl,
    }),

  // ⑥ 분석 상세보기 조회
  getAnalysisDetail: (sessionId: string, baseUrl?: string) =>
    apiRequest<AnalysisDetailData>(`/api/v1/sessions/${sessionId}/analysis-detail`, {
      method: 'GET',
      baseUrl,
    }),

  // ⑦ 얼굴형·체형·모질 수동 수정
  patchProfile: (sessionId: string, req: ProfilePatchRequest, baseUrl?: string) =>
    apiRequest<ProfilePatchData>(`/api/v1/sessions/${sessionId}/profile`, {
      method: 'PATCH',
      body: req,
      baseUrl,
    }),

  // ⑧ 세션 초기화 (부스 초기화)
  deleteSession: (sessionId: string, baseUrl?: string) =>
    apiRequest<SessionDeleteData>(`/api/v1/sessions/${sessionId}`, {
      method: 'DELETE',
      baseUrl,
      timeoutMs: 10_000,
    }),

  // ⑨ 챗봇 대화 조회 (화면 진입)
  getChatMessages: (sessionId: string, baseUrl?: string) =>
    apiRequest<ChatHistoryData>(`/api/v1/sessions/${sessionId}/chat/messages`, {
      method: 'GET',
      baseUrl,
    }),

  // ⑩ 챗봇 메시지 전송
  sendChatMessage: (
    sessionId: string,
    req: ChatSendRequest,
    mock?: MockHeadersConfig,
    baseUrl?: string,
  ) =>
    apiRequest<ChatSendData>(`/api/v1/sessions/${sessionId}/chat/messages`, {
      method: 'POST',
      body: req,
      headers: buildMockHeaders(mock, 'chat'),
      baseUrl,
      timeoutMs: 30_000,
      timeoutCode: 'CHAT_TIMEOUT',
      networkUnavailableCode: 'CHAT_SERVER_UNAVAILABLE',
    }),
};
