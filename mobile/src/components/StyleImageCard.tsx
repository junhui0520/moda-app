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
  const { images, baseUrl } = useStyleImages(imageKey);
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
          <Ionicons name={placeholder.icon} size={28} color="#A1A1AA" />
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

/** 챗봇 답변에 붙는 작은 썸네일 */
export function StyleThumb({ imageKey, label }: { imageKey: string; label: string }) {
  const { images, baseUrl } = useStyleImages(imageKey);
  if (!images) return null;
  return (
    <View style={styles.thumbWrap}>
      <Image source={{ uri: baseUrl + images.url }} style={styles.thumb} accessibilityLabel={label} />
      <Text style={styles.itemLabel} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  items: { flexDirection: 'row', gap: 6, paddingHorizontal: 12, paddingVertical: 10, backgroundColor: '#18181B' },
  item: { flex: 1, maxWidth: 76 },
  itemImage: { width: '100%', aspectRatio: 1, backgroundColor: '#27272A' },
  itemLabel: { fontSize: 10, color: '#A1A1AA', textAlign: 'center', marginTop: 3 },
  thumbWrap: { width: 120 },
  thumb: { width: 120, height: 120, backgroundColor: '#27272A' },
  wrapper: {
    width: '100%',
    backgroundColor: '#18181B',
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
    backgroundColor: '#18181B',
  },
  hairline: {
    width: 24,
    height: 1,
    backgroundColor: '#52525B',
    marginBottom: 14,
  },
  placeholderEyebrow: {
    marginTop: 10,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2.4,
    color: '#A1A1AA',
  },
  placeholderLabel: {
    marginTop: 4,
    fontSize: 18,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -0.3,
  },
  placeholderSub: {
    marginTop: 8,
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.8,
    color: '#71717A',
  },
  rankBadge: {
    position: 'absolute',
    top: 12,
    left: 12,
    backgroundColor: colors.primary,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: radius.xs,
  },
  rankText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
  },
});
