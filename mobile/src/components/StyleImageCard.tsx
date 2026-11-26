// src/components/StyleImageCard.tsx — 에디토리얼 룩북 스타일 이미지 카드 (imageKey 매핑)

import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getStyleImage, getStylePlaceholder } from '../constants/styleImages';
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
  const asset = getStyleImage(imageKey);
  const placeholder = getStylePlaceholder(imageKey);
  const rankStr = String(rank).padStart(2, '0');

  return (
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
  );
}

const styles = StyleSheet.create({
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
