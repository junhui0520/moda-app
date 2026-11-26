import { useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { BASE_URL } from '../api/client';
import { getStyleImages } from '../api/modaApi';
import type { StyleImages } from '../api/types';
import { colors } from '../theme';

/** large: 큰 사진(full_shot) + 아이템 작은 사진 줄 / small: 챗봇용 썸네일. 이미지가 없거나 불러오는 중이면 텍스트 박스 */
export function StyleImage({ imageKey, label, size = 'large' }: { imageKey: string; label: string; size?: 'large' | 'small' }) {
  const [images, setImages] = useState<StyleImages | null>(null);
  useEffect(() => {
    let alive = true;
    getStyleImages()
      .then((all) => alive && setImages(all[imageKey] ?? null))
      .catch(() => alive && setImages(null));
    return () => {
      alive = false;
    };
  }, [imageKey]);

  if (!images) {
    return (
      <View style={[styles.box, size === 'small' && styles.small]} accessibilityRole="image" accessibilityLabel={`${label} 스타일 이미지`}>
        <Text style={styles.label}>{label}</Text>
      </View>
    );
  }

  const main = (
    <Image
      source={{ uri: BASE_URL + images.url }}
      style={size === 'small' ? styles.thumb : styles.main}
      resizeMode="cover"
      accessibilityLabel={`${label} 스타일 이미지`}
    />
  );
  if (size === 'small') return main;

  return (
    <View style={styles.wrap}>
      {main}
      {images.items.length > 0 && (
        <View style={styles.items}>
          {images.items.map((item) => (
            <View key={item.url} style={styles.item}>
              <Image source={{ uri: BASE_URL + item.url }} style={styles.itemImage} accessibilityLabel={item.label} />
              <Text style={styles.itemLabel} numberOfLines={1}>{item.label}</Text>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    height: 140,
    borderRadius: 12,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: colors.border,
    backgroundColor: colors.muted,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 10,
  },
  small: { height: 120, width: 120, marginBottom: 0 },
  label: { fontSize: 14, fontWeight: '600', color: colors.subtext },
  wrap: { marginBottom: 10 },
  main: { width: '100%', aspectRatio: 1, borderRadius: 12, backgroundColor: colors.muted },
  thumb: { width: 120, height: 120, borderRadius: 10, backgroundColor: colors.muted },
  items: { flexDirection: 'row', gap: 6, marginTop: 8 },
  item: { flex: 1, maxWidth: 80 },
  itemImage: { width: '100%', aspectRatio: 1, borderRadius: 8, backgroundColor: colors.muted },
  itemLabel: { fontSize: 11, color: colors.subtext, textAlign: 'center', marginTop: 3 },
});
