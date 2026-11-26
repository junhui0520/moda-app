import { jsonBody, request } from './client';
import type { ChatHistoryData, ChatSendData, ProfileInputRequest, ResultData } from './types';

/** RN은 {uri,name,type}, 웹은 Blob을 FormData에 넣는다 */
export type UploadImage = Blob | { uri: string; name: string; type: string };

export const createSession = () =>
  request<{ sessionId: string }>('/sessions/guest', jsonBody('POST', { photoConsent: true }));

export const submitProfile = (sessionId: string, input: ProfileInputRequest) =>
  request(`/sessions/${sessionId}/profile-input`, jsonBody('POST', input));

export function analyze(sessionId: string, kind: 'face' | 'body', image: UploadImage, headers: Record<string, string> = {}) {
  const form = new FormData();
  if (image instanceof Blob) form.append('image', image, 'photo.png');
  else form.append('image', image as unknown as Blob);
  return request(`/sessions/${sessionId}/analyze/${kind}`, { method: 'POST', headers, body: form });
}

export const getResult = (sessionId: string) => request<ResultData>(`/sessions/${sessionId}/result`);

export const getChat = (sessionId: string) => request<ChatHistoryData>(`/sessions/${sessionId}/chat/messages`);

export const sendChat = (sessionId: string, message: string) =>
  request<ChatSendData>(`/sessions/${sessionId}/chat/messages`, jsonBody('POST', { message }));
