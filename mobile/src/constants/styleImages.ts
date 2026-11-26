// src/constants/styleImages.ts — imageKey → 로컬 에셋 매핑 (헤어 5종 + 패션 9종)
//
// 이미지 파일 확보 시 assets/styles/ 폴더에 파일명을 맞춰 넣고,
// 아래 STYLE_IMAGE_ASSETS의 null을 require('../../assets/styles/파일명.png')로 교체하면 자동 반영됩니다.

import type { ImageSourcePropType } from 'react-native';

export interface StylePlaceholderMeta {
  bg: string;
  accent: string;
  badge: string;
  icon: 'cut-outline' | 'shirt-outline';
}

export const STYLE_IMAGE_ASSETS: Record<string, ImageSourcePropType | null> = {
  // 헤어 5종
  hair_up_style: null,
  hair_parted_style: null,
  hair_dandy_style: null,
  hair_crop_style: null,
  hair_shadow_style: null,

  // 패션 9종
  fashion_casual: null,
  fashion_dandy: null,
  fashion_minimal: null,
  fashion_city_boy: null,
  fashion_amekaji: null,
  fashion_street: null,
  fashion_sporty: null,
  fashion_gorpcore: null,
  fashion_vintage: null,
};

export const STYLE_PLACEHOLDER_META: Record<string, StylePlaceholderMeta> = {
  // 헤어 5종
  hair_up_style: { bg: '#EEF2FF', accent: '#3730A3', badge: 'UP', icon: 'cut-outline' },
  hair_parted_style: { bg: '#E0F2FE', accent: '#0369A1', badge: 'PARTED', icon: 'cut-outline' },
  hair_dandy_style: { bg: '#F3F4F6', accent: '#1F2937', badge: 'DANDY', icon: 'cut-outline' },
  hair_crop_style: { bg: '#ECFDF5', accent: '#047857', badge: 'CROP', icon: 'cut-outline' },
  hair_shadow_style: { bg: '#F5F3FF', accent: '#6D28D9', badge: 'SHADOW', icon: 'cut-outline' },

  // 패션 9종
  fashion_casual: { bg: '#EFF6FF', accent: '#1D4ED8', badge: 'CASUAL', icon: 'shirt-outline' },
  fashion_dandy: { bg: '#F3F4F6', accent: '#111827', badge: 'DANDY', icon: 'shirt-outline' },
  fashion_minimal: { bg: '#F8FAFC', accent: '#334155', badge: 'MINIMAL', icon: 'shirt-outline' },
  fashion_city_boy: { bg: '#ECFEFF', accent: '#0E7490', badge: 'CITY BOY', icon: 'shirt-outline' },
  fashion_amekaji: { bg: '#FEF3C7', accent: '#92400E', badge: 'AMEKAJI', icon: 'shirt-outline' },
  fashion_street: { bg: '#1F2937', accent: '#F9FAFB', badge: 'STREET', icon: 'shirt-outline' },
  fashion_sporty: { bg: '#DCFCE7', accent: '#15803D', badge: 'SPORTY', icon: 'shirt-outline' },
  fashion_gorpcore: { bg: '#ECFCCB', accent: '#3F6212', badge: 'GORPCORE', icon: 'shirt-outline' },
  fashion_vintage: { bg: '#FFEDD5', accent: '#9A3412', badge: 'VINTAGE', icon: 'shirt-outline' },
};

export function getStyleImage(imageKey: string): ImageSourcePropType | null {
  return STYLE_IMAGE_ASSETS[imageKey] ?? null;
}

export function getStylePlaceholder(imageKey: string): StylePlaceholderMeta {
  return (
    STYLE_PLACEHOLDER_META[imageKey] ?? {
      bg: '#F3F4F6',
      accent: '#374151',
      badge: 'STYLE',
      icon: imageKey.startsWith('hair_') ? 'cut-outline' : 'shirt-outline',
    }
  );
}
