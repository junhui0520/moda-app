// src/store/useSessionStore.ts — 비로그인 게스트 세션 전역 상태 (Zustand)

import { create } from 'zustand';
import { DEFAULT_API_BASE_URL } from '../api/client';
import { modaApi } from '../api/modaApi';
import type {
  AnalysisDetailData,
  BodyAnalysisData,
  FaceAnalysisData,
  MockHeadersConfig,
  NextStep,
  ProfileInput,
  Progress,
  ResultData,
} from '../api/types';

interface SessionState {
  baseUrl: string;
  sessionId: string | null;
  expiresAt: string | null;
  progress: Progress;
  nextStep: NextStep;
  profileInput: ProfileInput | null;
  localFaceUri: string | null;
  localBodyUri: string | null;
  lastFaceAnalysis: FaceAnalysisData | null;
  lastBodyAnalysis: BodyAnalysisData | null;
  result: ResultData | null;
  analysisDetail: AnalysisDetailData | null;
  mockConfig: MockHeadersConfig;

  setBaseUrl: (url: string) => void;
  setMockConfig: (patch: Partial<MockHeadersConfig>) => void;
  resetMockConfig: () => void;

  setSession: (data: {
    sessionId: string;
    expiresAt: string;
    progress: Progress;
    nextStep: NextStep;
  }) => void;
  setProfileState: (data: {
    profileInput: ProfileInput;
    progress: Progress;
    nextStep: NextStep;
    expiresAt: string;
    bodyAnalysisReset?: boolean;
  }) => void;
  setLocalFaceUri: (uri: string | null) => void;
  setLocalBodyUri: (uri: string | null) => void;
  setFaceAnalysis: (data: FaceAnalysisData) => void;
  setBodyAnalysis: (data: BodyAnalysisData) => void;
  setResult: (result: ResultData) => void;
  setAnalysisDetail: (detail: AnalysisDetailData | null) => void;
  syncAfterPatch: (data: {
    progress: Progress;
    nextStep: NextStep;
    expiresAt: string;
    result: ResultData | null;
  }) => void;

  resetSessionLocal: () => void;
  resetSessionWithApi: () => Promise<void>;
}

const initialProgress: Progress = {
  profileInput: false,
  faceAnalysis: false,
  bodyAnalysis: false,
};

const initialMockConfig: MockHeadersConfig = {
  faceShape: '',
  bodyType: '',
  unusable: false,
  borderline: false,
  bodyMinimal: false,
  delayMs: null,
};

export const useSessionStore = create<SessionState>((set, get) => ({
  baseUrl: DEFAULT_API_BASE_URL,
  sessionId: null,
  expiresAt: null,
  progress: { ...initialProgress },
  nextStep: 'PROFILE_INPUT',
  profileInput: null,
  localFaceUri: null,
  localBodyUri: null,
  lastFaceAnalysis: null,
  lastBodyAnalysis: null,
  result: null,
  analysisDetail: null,
  mockConfig: { ...initialMockConfig },

  setBaseUrl: (url) => set({ baseUrl: url.trim() || DEFAULT_API_BASE_URL }),
  setMockConfig: (patch) =>
    set((state) => ({
      mockConfig: { ...state.mockConfig, ...patch },
    })),
  resetMockConfig: () => set({ mockConfig: { ...initialMockConfig } }),

  setSession: ({ sessionId, expiresAt, progress, nextStep }) =>
    set({
      sessionId,
      expiresAt,
      progress,
      nextStep,
      profileInput: null,
      localFaceUri: null,
      localBodyUri: null,
      lastFaceAnalysis: null,
      lastBodyAnalysis: null,
      result: null,
      analysisDetail: null,
    }),

  setProfileState: ({ profileInput, progress, nextStep, expiresAt, bodyAnalysisReset }) =>
    set((state) => ({
      profileInput,
      progress,
      nextStep,
      expiresAt,
      ...(bodyAnalysisReset
        ? { localBodyUri: null, lastBodyAnalysis: null, result: null, analysisDetail: null }
        : {}),
    })),

  setLocalFaceUri: (uri) => set({ localFaceUri: uri }),
  setLocalBodyUri: (uri) => set({ localBodyUri: uri }),

  setFaceAnalysis: (data) =>
    set({
      lastFaceAnalysis: data,
      progress: data.progress,
      nextStep: data.nextStep,
      expiresAt: data.expiresAt,
    }),

  setBodyAnalysis: (data) =>
    set({
      lastBodyAnalysis: data,
      progress: data.progress,
      nextStep: data.nextStep,
      expiresAt: data.expiresAt,
    }),

  setResult: (result) => set({ result }),
  setAnalysisDetail: (detail) => set({ analysisDetail: detail }),

  syncAfterPatch: ({ progress, nextStep, expiresAt, result }) =>
    set((state) => ({
      progress,
      nextStep,
      expiresAt,
      result: result ?? state.result,
    })),

  resetSessionLocal: () =>
    set({
      sessionId: null,
      expiresAt: null,
      progress: { ...initialProgress },
      nextStep: 'PROFILE_INPUT',
      profileInput: null,
      localFaceUri: null,
      localBodyUri: null,
      lastFaceAnalysis: null,
      lastBodyAnalysis: null,
      result: null,
      analysisDetail: null,
    }),

  resetSessionWithApi: async () => {
    const { sessionId, baseUrl, resetSessionLocal } = get();
    if (sessionId) {
      try {
        await modaApi.deleteSession(sessionId, baseUrl);
      } catch {
        // 명세: 삭제 API 실패 시에도 로컬 상태·사진 URI 초기화 후 시작 화면 이동
      }
    }
    resetSessionLocal();
  },
}));
