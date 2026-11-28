// src/components/StyleImageCard.tsx — 에디토리얼 룩북 스타일 이미지 카드 (imageKey 매핑)

import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getStyleImage, getStylePlaceholder } from '../constants/styleImages';
import { useStyleImages } from '../hooks/useStyleImages';
import { colors, radius } from '../theme/theme';

interface StyleImageCardProps {
  imageKey: string;
  label: string;
  rank: number;
  total?: number;
  height?: number;
}

export function StyleImageCard({
  imageKey,
  label,
  rank,
  total,
  height = 240,
}: StyleImageCardProps) {
  const { images: picked, baseUrl } = useStyleImages(imageKey);
  const images = picked[0];
  // 서버 DB 이미지 > 로컬 에셋 > placeholder
  const asset = images ? { uri: baseUrl + images.url } : getStyleImage(imageKey);
  const placeholder = getStylePlaceholder(imageKey);
  const rankStr = String(rank).padStart(2, '0');

  return (
    <View>
    <View style={[styles.wrapper, { height }]}>
      {asset ? (
        <Image source={asset} style={styles.image} resizeMode="cover" />
      ) : (
        <View style={styles.editorialPlaceholder}>
          <View style={styles.hairline} />
          <Ionicons name={placeholder.icon} size={28} color="#8E8E93" />
          <Text style={styles.placeholderEyebrow}>{placeholder.badge}</Text>
          <Text style={styles.placeholderLabel} numberOfLines={1}>
            {label}
          </Text>
          <Text style={styles.placeholderSub}>STYLE LOOKBOOK</Text>
        </View>
      )}

      <View style={styles.rankBadge}>
        <Text style={styles.rankText}>
          {rankStr}
          {total ? ` / 0${total}` : ''}
        </Text>
      </View>
    </View>
    {images && images.items.length > 0 ? (
      <View style={styles.items}>
        {images.items.map((item) => (
          <View key={item.url} style={styles.item}>
            <Image source={{ uri: baseUrl + item.url }} style={styles.itemImage} />
            <Text style={styles.itemLabel} numberOfLines={1}>
              {item.label}
            </Text>
          </View>
        ))}
      </View>
    ) : null}
    </View>
  );
}

/** 챗봇 답변에 붙는 썸네일: 스타일당 서로 다른 코디(full_shot) 랜덤 3장 */
export function StyleThumb({ imageKey, label }: { imageKey: string; label: string }) {
  const { images, baseUrl } = useStyleImages(imageKey, 3);
  if (images.length === 0) return null;
  return (
    <View>
      <View style={{ flexDirection: 'row', gap: 6 }}>
        {images.map((img) => (
          <Image key={img.url} source={{ uri: baseUrl + img.url }} style={styles.thumb} accessibilityLabel={label} />
        ))}
      </View>
      <Text style={styles.itemLabel} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  items: { flexDirection: 'row', gap: 8, paddingHorizontal: 12, paddingVertical: 10, backgroundColor: '#F2F2F7' },
  item: { flex: 1, maxWidth: 76 },
  itemImage: { width: '100%', aspectRatio: 1, borderRadius: radius.xs, backgroundColor: '#E5E5EA' },
  itemLabel: { fontSize: 11, color: '#6E6E73', textAlign: 'center', marginTop: 4, fontWeight: '500' },
  thumb: { width: 84, height: 112, borderRadius: radius.sm, backgroundColor: '#E5E5EA' },
  wrapper: {
    width: '100%',
    backgroundColor: '#1C1C1E',
    overflow: 'hidden',
    position: 'relative',
    justifyContent: 'center',
    alignItems: 'center',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  editorialPlaceholder: {
    flex: 1,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
    backgroundColor: '#1C1C1E',
  },
  hairline: {
    width: 24,
    height: 1,
    backgroundColor: '#48484A',
    marginBottom: 14,
  },
  placeholderEyebrow: {
    marginTop: 10,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.6,
    color: '#AEAEB2',
  },
  placeholderLabel: {
    marginTop: 4,
    fontSize: 18,
    fontWeight: '700',
    color: '#FFFFFF',
    letterSpacing: -0.3,
  },
  placeholderSub: {
    marginTop: 6,
    fontSize: 11,
    fontWeight: '500',
    color: '#8E8E93',
  },
  rankBadge: {
    position: 'absolute',
    top: 12,
    left: 12,
    backgroundColor: 'rgba(28, 28, 30, 0.78)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: radius.full,
  },
  rankText: {
    color: '#FFFFFF',
    fontSize: 11.5,
    fontWeight: '700',
  },
});
