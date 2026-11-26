import { jsonBody, request } from './client';
import type { ChatHistoryData, ChatSendData, ProfileInputRequest, ResultData, StyleImages } from './types';

/** Expo의 fetch는 FormData에 Blob만 받음 ({uri,name,type} 객체는 Unsupported FormDataPart). 사진 URI는 fetch(uri).blob()으로 변환 */
export type UploadImage = Blob;

export const createSession = () =>
  request<{ sessionId: string }>('/sessions/guest', jsonBody('POST', { photoConsent: true }));

export const submitProfile = (sessionId: string, input: ProfileInputRequest) =>
  request(`/sessions/${sessionId}/profile-input`, jsonBody('POST', input));

export function analyze(sessionId: string, kind: 'face' | 'body', image: UploadImage, headers: Record<string, string> = {}) {
  const form = new FormData();
  form.append('image', image, 'photo.png');
  return request(`/sessions/${sessionId}/analyze/${kind}`, { method: 'POST', headers, body: form });
}

export const getResult = (sessionId: string) => request<ResultData>(`/sessions/${sessionId}/result`);

export const getChat = (sessionId: string) => request<ChatHistoryData>(`/sessions/${sessionId}/chat/messages`);

export const sendChat = (sessionId: string, message: string) =>
  request<ChatSendData>(`/sessions/${sessionId}/chat/messages`, jsonBody('POST', { message }));

// 앱 실행 중 1번만 받음 (실패하면 다음 호출에서 다시 시도)
let styleImages: Promise<Record<string, StyleImages>> | null = null;
export const getStyleImages = () =>
  (styleImages ??= request<Record<string, StyleImages>>('/style-images').catch((e) => {
    styleImages = null;
    throw e;
  }));
