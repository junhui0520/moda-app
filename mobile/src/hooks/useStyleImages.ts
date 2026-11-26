// src/hooks/useStyleImages.ts — 서버 DB의 스타일 이미지 경로 (imageKey → 큰 사진 + 아이템 사진)

import { useEffect, useState } from 'react';
import { modaApi } from '../api/modaApi';
import type { StyleImages } from '../api/types';
import { useSessionStore } from '../store/useSessionStore';

// 서버 주소별로 1번만 받음 (실패하면 다음 호출에서 다시 시도)
const cache = new Map<string, Promise<Record<string, StyleImages>>>();

function load(baseUrl: string) {
  let p = cache.get(baseUrl);
  if (!p) {
    p = modaApi.getStyleImages(baseUrl).catch((e) => {
      cache.delete(baseUrl);
      throw e;
    });
    cache.set(baseUrl, p);
  }
  return p;
}

/** 이미지가 없거나 불러오는 중이면 images = null → 호출한 쪽이 기존 placeholder 표시 */
export function useStyleImages(imageKey: string): { images: StyleImages | null; baseUrl: string } {
  const baseUrl = useSessionStore((s) => s.baseUrl).replace(/\/+$/, '');
  const [images, setImages] = useState<StyleImages | null>(null);

  useEffect(() => {
    let alive = true;
    load(baseUrl)
      .then((all) => alive && setImages(all[imageKey] ?? null))
      .catch(() => alive && setImages(null));
    return () => {
      alive = false;
    };
  }, [baseUrl, imageKey]);

  return { images, baseUrl };
}
