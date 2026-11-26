import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { FashionRecommendation, HairRecommendation, ResultData } from '../api/types';
import { StyleImage } from '../components/StyleImage';
import { colors } from '../theme';

export function ResultScreen({ result, onChat, onRestart }: { result: ResultData; onChat: () => void; onRestart: () => void }) {
  const { type, basis } = result;
  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.caption}>나의 맞춤 유형</Text>
      <Text style={styles.typeName}>{type.typeName}</Text>
      <Text style={styles.headline}>{type.headline}</Text>
      <Text style={styles.basis}>
        {basis.faceShape.label} · {basis.bodyType.label}
      </Text>
      <Text style={styles.body}>{type.description}</Text>
      {type.stylePoints.map((p) => (
        <Text key={p} style={styles.point}>• {p}</Text>
      ))}

      <Text style={styles.section}>추천 헤어스타일</Text>
      {result.hairStyles.map((h) => <StyleCard key={h.code} item={h} />)}

      <Text style={styles.section}>추천 패션 스타일</Text>
      {result.fashionStyles.map((f) => <StyleCard key={f.code} item={f} />)}

      <Pressable style={styles.primary} onPress={onChat} accessibilityRole="button">
        <Text style={styles.primaryText}>챗봇 이용하기</Text>
      </Pressable>
      <Pressable style={styles.secondary} onPress={onRestart} accessibilityRole="button">
        <Text style={styles.secondaryText}>처음부터 다시 하기</Text>
      </Pressable>
      <Text style={styles.disclaimer}>{result.disclaimer}</Text>
    </ScrollView>
  );
}

function StyleCard({ item }: { item: HairRecommendation | FashionRecommendation }) {
  return (
    <View style={styles.card}>
      <StyleImage imageKey={item.imageKey} label={item.name} />
      <Text style={styles.cardTitle}>{item.rank}. {item.name}</Text>
      <Text style={styles.body}>{item.description}</Text>
      {item.reasons.map((r) => (
        <Text key={r} style={styles.point}>• {r}</Text>
      ))}
      {'itemKeywords' in item && (
        <View style={styles.tags}>
          {item.itemKeywords.map((k) => (
            <Text key={k} style={styles.tag}>{k}</Text>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 20, paddingBottom: 40 },
  caption: { fontSize: 13, color: colors.subtext },
  typeName: { fontSize: 28, fontWeight: '800', color: colors.primary, marginTop: 4 },
  headline: { fontSize: 16, fontWeight: '600', color: colors.text, marginTop: 6 },
  basis: { fontSize: 14, color: colors.subtext, marginTop: 4, marginBottom: 12 },
  body: { fontSize: 15, color: colors.text, lineHeight: 22, marginBottom: 6 },
  point: { fontSize: 14, color: colors.subtext, lineHeight: 21 },
  section: { fontSize: 19, fontWeight: '700', color: colors.text, marginTop: 28, marginBottom: 12 },
  card: { borderWidth: 1, borderColor: colors.border, borderRadius: 14, padding: 14, marginBottom: 14 },
  cardTitle: { fontSize: 17, fontWeight: '700', color: colors.text, marginBottom: 6 },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 10 },
  tag: { backgroundColor: colors.muted, color: colors.text, fontSize: 13, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999 },
  primary: { backgroundColor: colors.primary, borderRadius: 12, minHeight: 50, alignItems: 'center', justifyContent: 'center', marginTop: 16 },
  primaryText: { color: colors.onPrimary, fontSize: 16, fontWeight: '700' },
  secondary: { borderRadius: 12, minHeight: 48, alignItems: 'center', justifyContent: 'center', marginTop: 8 },
  secondaryText: { color: colors.subtext, fontSize: 15 },
  disclaimer: { fontSize: 12, color: colors.subtext, marginTop: 16, lineHeight: 18 },
});
