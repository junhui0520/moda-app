// docs/api-spec.md 부록 B 중 이 앱이 쓰는 타입만 (v1.2 + 챗봇 images)

export type FaceShapeKey = 'diamond' | 'heart' | 'oval' | 'rectangle' | 'round' | 'long';
export type BodyTypeKey = 'standard' | 'small_inv_triangle' | 'large_rectangle' | 'rectangle';
export type HairThickness = 'THIN' | 'MEDIUM' | 'THICK';
export type HairCurl = 'STRAIGHT' | 'SEMI_CURLY' | 'CURLY';
export type HairDensity = 'LOW' | 'MEDIUM' | 'HIGH';
export type ChatRole = 'USER' | 'ASSISTANT';

export interface CodeLabel<K extends string = string> { key: K; label: string; }

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details: { field: string; reason: string; rejectedValue: unknown }[];
  meta: Record<string, unknown> | null;
  path: string;
  timestamp: string;
}

export type ApiResponse<T> = { success: true; data: T } | { success: false; error: ApiError };

export interface ProfileInputRequest {
  age: number;
  heightCm: number;
  weightKg?: number | null;
  hairThickness: HairThickness;
  hairCurl: HairCurl;
  hairDensity: HairDensity;
}

export interface HairRecommendation {
  rank: number;
  code: string;
  name: string;
  description: string;
  imageKey: string;
  reasons: string[];
}
export interface FashionRecommendation extends HairRecommendation { itemKeywords: string[]; }

export interface ResultData {
  sessionId: string;
  type: {
    typeCode: string;
    typeName: string;
    nameConfirmed: boolean;
    headline: string;
    description: string;
    stylePoints: string[];
  };
  basis: { faceShape: CodeLabel<FaceShapeKey>; bodyType: CodeLabel<BodyTypeKey> };
  hairStyles: HairRecommendation[];
  fashionStyles: FashionRecommendation[];
  disclaimer: string;
}

/** 챗봇 답변에 붙는 스타일 이미지. 지금은 텍스트로 표시, DB 이미지 연동 시 imageKey로 찾음 */
export interface ChatImage { imageKey: string; label: string; }

export interface ChatMessage {
  messageId: string;
  role: ChatRole;
  content: string;
  images: ChatImage[];
  createdAt: string;
}

export interface ChatHistoryData {
  sessionId: string;
  welcomeMessage: string;
  messages: ChatMessage[];
  suggestedPrompts: string[];
  remainingTurns: number;
  maxTurns: number;
}

export interface ChatSendData {
  sessionId: string;
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  suggestedPrompts: string[];
  remainingTurns: number;
  maxTurns: number;
}
