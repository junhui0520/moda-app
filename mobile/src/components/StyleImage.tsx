import { StyleSheet, Text, View } from 'react-native';
import { colors } from '../theme';

// shortcut: 이미지 대신 텍스트 박스. DB 이미지가 준비되면 imageKey → 이미지 URL로 찾아 <Image>로 교체
export function StyleImage({ imageKey, label, size = 'large' }: { imageKey: string; label: string; size?: 'large' | 'small' }) {
  return (
    <View
      style={[styles.box, size === 'small' && styles.small]}
      accessibilityRole="image"
      accessibilityLabel={`${label} 스타일 이미지`}
    >
      <Text style={styles.label}>[이미지] {label}</Text>
      <Text style={styles.key}>{imageKey}</Text>
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
  small: { height: 72, width: 140, marginBottom: 0, marginRight: 8 },
  label: { fontSize: 14, fontWeight: '600', color: colors.text },
  key: { fontSize: 11, color: colors.subtext, marginTop: 4 },
});
