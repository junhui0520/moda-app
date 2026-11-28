// src/theme/theme.ts — iOS 미니멀 뉴트럴 디자인 토큰 (오프화이트 · 딥 차콜 · 쿨 그레이 · 슬레이트 블루 포인트)

export const colors = {
  bg: '#F8F9FA', // 오프화이트 배경
  surface: '#FFFFFF', // 순백색 카드
  surfaceMuted: '#E5E7EB', // 쿨 그레이 보조 트랙·배지
  surfaceDark: '#1A1A1A', // 딥 차콜 다크 컨테이너
  primary: '#1A1A1A', // 딥 차콜 — 메인 텍스트 & 핵심 버튼
  primarySoft: '#2E2E2E',
  accent: '#3B82F6', // 슬레이트 블루 — 얼굴형·체형 진단 수치 및 강조 배지 전용
  accentLight: '#EFF6FF', // 연한 슬레이트 블루 배지 배경
  accentBorder: '#BFDBFE',
  text: '#1A1A1A',
  textSecondary: '#52525B',
  textMuted: '#8E929B',
  textOnDark: '#FFFFFF',
  border: '#E5E7EB', // 쿨 그레이 구분선
  borderStrong: '#D1D5DB',
  success: '#16A34A',
  successBg: '#F0FDF4',
  warning: '#D97706',
  warningBg: '#FFFBEB',
  warningBorder: '#FDE68A',
  danger: '#DC2626',
  dangerBg: '#FEF2F2',
  dangerBorder: '#FECACA',
};

export const radius = {
  none: 0,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  full: 999,
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 28,
};
