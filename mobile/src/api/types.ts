// src/api/types.ts — docs/api-spec.md v1.2 기준

export type FaceShapeKey = 'diamond' | 'heart' | 'oval' | 'rectangle' | 'round' | 'long';
export type BodyTypeKey = 'standard' | 'small_inv_triangle' | 'large_rectangle' | 'rectangle';
export type HairThickness = 'THIN' | 'MEDIUM' | 'THICK';
export type HairCurl = 'STRAIGHT' | 'SEMI_CURLY' | 'CURLY';
export type HairDensity = 'LOW' | 'MEDIUM' | 'HIGH';
export type HairStyleCode = 'UP_STYLE' | 'PARTED_STYLE' | 'DANDY_STYLE' | 'CROP_STYLE' | 'SHADOW_STYLE';
export type FashionStyleCode =
  | 'CASUAL'
  | 'DANDY'
  | 'MINIMAL'
  | 'CITY_BOY'
  | 'AMEKAJI'
  | 'STREET'
  | 'SPORTY'
  | 'GORPCORE'
  | 'VINTAGE';
export type NextStep = 'PROFILE_INPUT' | 'FACE_ANALYSIS' | 'BODY_ANALYSIS' | 'RESULT_READY';
export type AnalysisSource = 'AI' | 'MANUAL';
export type AnalysisStatus = 'ANALYZED' | 'CORRECTED' | 'MANUAL' | 'NOT_ANALYZED';
export type WarningSeverity = 'RETAKE' | 'INFO';
export type SizeKoreaDirection = 'ABOVE' | 'BELOW' | 'SIMILAR';
export type ChatRole = 'USER' | 'ASSISTANT';

export type ErrorCode =
  | 'INVALID_REQUEST'
  | 'PHOTO_CONSENT_REQUIRED'
  | 'IMAGE_REQUIRED'
  | 'SESSION_NOT_FOUND'
  | 'PROFILE_INPUT_REQUIRED'
  | 'ANALYSIS_INCOMPLETE'
  | 'ANALYSIS_IN_PROGRESS'
  | 'SESSION_EXPIRED'
  | 'IMAGE_TOO_LARGE'
  | 'UNSUPPORTED_IMAGE_TYPE'
  | 'INTERNAL_ERROR'
  | 'AI_BAD_RESPONSE'
  | 'AI_SERVER_UNAVAILABLE'
  | 'AI_TIMEOUT'
  | 'CHAT_TURN_LIMIT_EXCEEDED'
  | 'CHAT_SERVER_UNAVAILABLE'
  | 'CHAT_TIMEOUT';

export interface CodeLabel<K extends string = string> {
  key: K;
  label: string;
}

export interface Progress {
  profileInput: boolean;
  faceAnalysis: boolean;
  bodyAnalysis: boolean;
}

export interface Warning {
  code: string;
  severity: WarningSeverity;
  message: string;
}

export interface FieldError {
  field: string;
  reason: string;
  rejectedValue: unknown | null;
}

export interface ApiError {
  status: number;
  code: ErrorCode;
  message: string;
  details: FieldError[];
  meta: Record<string, unknown> | null;
  path: string;
  timestamp: string;
}

export type ApiResponse<T> = { success: true; data: T } | { success: false; error: ApiError };

// ① POST /sessions/guest
export interface GuestSessionRequest {
  photoConsent: true;
  deviceLabel?: string | null;
}
export interface GuestSessionData {
  sessionId: string;
  createdAt: string;
  expiresAt: string;
  ttlMinutes: number;
  progress: Progress;
  nextStep: NextStep;
}

// ② POST /profile-input
export interface ProfileInput {
  age: number;
  heightCm: number;
  weightKg: number | null;
  hairThickness: HairThickness;
  hairCurl: HairCurl;
  hairDensity: HairDensity;
}
export type ProfileInputRequest = Omit<ProfileInput, 'weightKg'> & { weightKg?: number | null };
export interface ProfileInputData {
  sessionId: string;
  profileInput: ProfileInput;
  bodyAnalysisReset: boolean;
  progress: Progress;
  nextStep: NextStep;
  expiresAt: string;
}

// ③ POST /analyze/face
export interface FaceAnalysisData {
  sessionId: string;
  analysisType: 'FACE';
  usable: boolean;
  retakeRequired: boolean;
  faceShape: CodeLabel<FaceShapeKey> | null;
  confidence: number | null;
  borderline: boolean | null;
  warnings: Warning[];
  progress: Progress;
  nextStep: NextStep;
  analyzedAt: string;
  expiresAt: string;
}

// ④ POST /analyze/body
export interface BodyAnalysisData {
  sessionId: string;
  analysisType: 'BODY';
  usable: boolean;
  retakeRequired: boolean;
  bodyType: CodeLabel<BodyTypeKey> | null;
  confidence: number | null;
  borderline: boolean | null;
  heightCmUsed: number;
  warnings: Warning[];
  progress: Progress;
  nextStep: NextStep;
  analyzedAt: string;
  expiresAt: string;
}

// ⑤ GET /result
export interface Adjustment {
  code: string;
  factor: 'hairThickness' | 'hairCurl' | 'hairDensity' | 'bmi' | 'height' | 'age';
  value: string;
  delta: number;
}
export interface ScoreBreakdown {
  base: number;
  adjustments: Adjustment[];
}

export interface HairRecommendation {
  rank: number;
  code: HairStyleCode;
  name: string;
  description: string;
  imageKey: string;
  score: number;
  reasons: string[];
  reasonCodes: string[];
  scoreBreakdown: ScoreBreakdown;
}
export interface FashionRecommendation extends Omit<HairRecommendation, 'code'> {
  code: FashionStyleCode;
  itemKeywords: string[];
}

export interface TypeResult {
  typeCode: string;
  typeName: string;
  nameConfirmed: boolean;
  headline: string;
  description: string;
  stylePoints: string[];
}
export interface ResultBasis {
  faceShape: CodeLabel<FaceShapeKey>;
  bodyType: CodeLabel<BodyTypeKey>;
  faceSource: AnalysisSource;
  bodySource: AnalysisSource;
}
export interface ResultData {
  sessionId: string;
  ruleVersion: string;
  generatedAt: string;
  type: TypeResult;
  basis: ResultBasis;
  hairStyles: HairRecommendation[];
  fashionStyles: FashionRecommendation[];
  disclaimer: string;
}

// ⑥ GET /analysis-detail
export interface Candidate {
  key: string;
  label: string;
  probability: number;
}
export interface Feature {
  key: string;
  label: string;
  value: number;
  unit: 'ratio' | 'deg' | 'percent';
  description: string | null;
}
export interface SizeKoreaItem {
  key: string;
  label: string;
  userMm: number;
  refMm: number;
  deviation: number;
  deviationPercent: number;
  direction: SizeKoreaDirection;
}
export interface SizeKoreaCompare {
  reference: string;
  referenceLabel: string;
  heightCm: number;
  items: SizeKoreaItem[];
  summary: { shoulderDeviation: number; trunkDeviation: number } | null;
}
export interface FaceDetail {
  status: AnalysisStatus;
  source: AnalysisSource | null;
  result: CodeLabel<FaceShapeKey> | null;
  aiResult: CodeLabel<FaceShapeKey> | null;
  description: string | null;
  aiReason: string | null;
  confidence: number | null;
  borderline: boolean | null;
  candidates: Candidate[];
  warnings: Warning[];
  features: Feature[];
  analyzedAt: string | null;
}
export interface BodyDetail extends Omit<FaceDetail, 'result' | 'aiResult' | 'aiReason'> {
  result: CodeLabel<BodyTypeKey> | null;
  aiResult: CodeLabel<BodyTypeKey> | null;
  heightCmUsed: number | null;
  compareToSizeKorea: SizeKoreaCompare | null;
}
export interface AnalysisDetailData {
  sessionId: string;
  face: FaceDetail;
  body: BodyDetail;
  profileInput: ProfileInput | null;
  editableOptions: {
    faceShapes: CodeLabel<FaceShapeKey>[];
    bodyTypes: CodeLabel<BodyTypeKey>[];
  };
}

// ⑦ PATCH /profile
export interface ProfilePatchRequest {
  faceShape?: FaceShapeKey | null;
  bodyType?: BodyTypeKey | null;
  hairThickness?: HairThickness | null;
  hairCurl?: HairCurl | null;
  hairDensity?: HairDensity | null;
}
export interface ProfilePatchData {
  sessionId: string;
  changedFields: string[];
  progress: Progress;
  nextStep: NextStep;
  expiresAt: string;
  result: ResultData | null;
}

// ⑧ DELETE /sessions/{sessionId}
export interface SessionDeleteData {
  sessionId: string;
  deleted: true;
  alreadyDeleted: boolean;
  deletedPhotoCount: number;
  deletedAt: string;
}

// ⑨·⑩ 챗봇 공통
export interface ChatMessage {
  messageId: string;
  role: ChatRole;
  content: string;
  createdAt: string;
}

// ⑨ GET /chat/messages
export interface ChatHistoryData {
  sessionId: string;
  welcomeMessage: string;
  messages: ChatMessage[];
  suggestedPrompts: string[];
  remainingTurns: number;
  maxTurns: number;
  expiresAt: string;
}

// ⑩ POST /chat/messages
export interface ChatSendRequest {
  message: string;
}
export interface ChatSendData {
  sessionId: string;
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  suggestedPrompts: string[];
  remainingTurns: number;
  maxTurns: number;
  expiresAt: string;
}

// Mock 모드 테스트 헤더 옵션 (moda.ai.mode=mock / moda.chat.mode=mock)
export interface MockHeadersConfig {
  faceShape?: FaceShapeKey | '';
  bodyType?: BodyTypeKey | '';
  unusable?: boolean;
  borderline?: boolean;
  bodyMinimal?: boolean;
  delayMs?: number | null;
}
