// src/hooks/useStyleImages.ts — 서버 DB의 스타일 이미지 경로 (imageKey → 큰 사진 + 아이템 사진)

import { useEffect, useState } from 'react';
import { useIsFocused } from '@react-navigation/native';
import { modaApi } from '../api/modaApi';
import type { StyleImages } from '../api/types';
import { useSessionStore } from '../store/useSessionStore';

// 서버 주소별로 1번만 받음 (실패하면 다음 호출에서 다시 시도)
const cache = new Map<string, Promise<Record<string, StyleImages[]>>>();

function load(baseUrl: string) {
  let p = cache.get(baseUrl);
  if (!p) {
    p = modaApi.getStyleImages(baseUrl).then((all) => {
      // 옛 형식(스타일당 객체 1개)이면 캐시하지 않음 → 서버를 새 버전으로 재시작하면 앱 리로드 없이 반영
      if (Object.values(all).some((v) => !Array.isArray(v))) cache.delete(baseUrl);
      return all;
    }).catch((e) => {
      cache.delete(baseUrl);
      throw e;
    });
    cache.set(baseUrl, p);
  }
  return p;
}

function sample<T>(list: T[], n: number): T[] {
  return [...list].sort(() => Math.random() - 0.5).slice(0, n);
}

/**
 * 스타일의 여러 사진 중 랜덤 `count`장. 화면에 들어올 때마다 새로 뽑고 리렌더에는 유지
 * (navigate는 스택에 있던 화면을 재사용해서 마운트만으로는 다시 안 뽑힘).
 * 이미지가 없거나 불러오는 중이면 images = [] → 호출한 쪽이 기존 placeholder 표시
 */
export function useStyleImages(imageKey: string, count = 1): { images: StyleImages[]; baseUrl: string } {
  const baseUrl = useSessionStore((s) => s.baseUrl).replace(/\/+$/, '');
  const [images, setImages] = useState<StyleImages[]>([]);
  const focused = useIsFocused();

  useEffect(() => {
    if (!focused) return;
    let alive = true;
    load(baseUrl)
      // 옛 서버(배포 서버 등)는 스타일당 객체 1개를 줌 → 배열로 감싸서 둘 다 지원
      .then((all) => alive && setImages(sample([all[imageKey] ?? []].flat(), count)))
      .catch(() => alive && setImages([]));
    return () => {
      alive = false;
    };
  }, [baseUrl, imageKey, count, focused]);

  return { images, baseUrl };
}
