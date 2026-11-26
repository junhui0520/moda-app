import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type ScrollViewInstance,
} from 'react-native';
import { ApiException } from '../api/client';
import { getChat, sendChat } from '../api/modaApi';
import type { ChatMessage } from '../api/types';
import { StyleImage } from '../components/StyleImage';
import { colors } from '../theme';

const MAX_LENGTH = 500;
const PENDING_ID = 'pending';

export function ChatScreen({ sessionId, onBack }: { sessionId: string; onBack: () => void }) {
  const [welcome, setWelcome] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [prompts, setPrompts] = useState<string[]>([]);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<{ message: string; retryText?: string } | null>(null);
  const scrollRef = useRef<ScrollViewInstance>(null);

  useEffect(() => {
    getChat(sessionId)
      .then((d) => {
        setWelcome(d.welcomeMessage);
        setMessages(d.messages);
        setPrompts(d.suggestedPrompts);
        setRemaining(d.remainingTurns);
      })
      .catch((e: Error) => setError({ message: e.message }));
  }, [sessionId]);

  async function send(raw: string) {
    const text = raw.trim();
    if (!text || sending || remaining === 0) return;
    setSending(true);
    setError(null);
    setInput('');
    const pending: ChatMessage = { messageId: PENDING_ID, role: 'USER', content: text, images: [], createdAt: '' };
    setMessages((m) => [...m, pending]);
    try {
      const d = await sendChat(sessionId, text);
      setMessages((m) => [...m.filter((x) => x.messageId !== PENDING_ID), d.userMessage, d.assistantMessage]);
      setPrompts(d.suggestedPrompts);
      setRemaining(d.remainingTurns);
    } catch (e) {
      setMessages((m) => m.filter((x) => x.messageId !== PENDING_ID));
      if (e instanceof ApiException && e.error.code === 'CHAT_TURN_LIMIT_EXCEEDED') {
        setRemaining(0);
        setError({ message: e.error.message });
      } else {
        setInput(text); // 실패한 문장은 유지 (턴 차감 없음)
        setError({ message: (e as Error).message, retryText: text });
      }
    } finally {
      setSending(false);
    }
  }

  const disabled = sending || remaining === 0 || remaining === null;

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.header}>
        <Pressable onPress={onBack} accessibilityRole="button" accessibilityLabel="결과 화면으로" style={styles.back}>
          <Text style={styles.backText}>‹ 결과</Text>
        </Pressable>
        <Text style={styles.title}>Moda 스타일 챗봇</Text>
        <Text style={styles.turns}>{remaining === null ? '' : `남은 대화 ${remaining}회`}</Text>
      </View>

      <ScrollView
        ref={scrollRef}
        style={styles.flex}
        contentContainerStyle={styles.list}
        onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
      >
        {welcome !== '' && <Bubble role="ASSISTANT" content={welcome} />}
        {messages.map((m) => (
          <Bubble key={m.messageId} role={m.role} content={m.content} images={m.images} />
        ))}
        {sending && (
          <View style={[styles.bubble, styles.assistant, styles.typing]}>
            <ActivityIndicator size="small" color={colors.subtext} />
            <Text style={styles.typingText}>입력 중…</Text>
          </View>
        )}
      </ScrollView>

      {error && (
        <View style={styles.errorRow}>
          <Text style={styles.error}>{error.message}</Text>
          {error.retryText && (
            <Pressable onPress={() => send(error.retryText!)} accessibilityRole="button" style={styles.retry}>
              <Text style={styles.retryText}>다시 보내기</Text>
            </Pressable>
          )}
        </View>
      )}

      {prompts.length > 0 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipBar} contentContainerStyle={styles.chips}>
          {prompts.map((p) => (
            <Pressable key={p} onPress={() => send(p)} disabled={disabled} style={[styles.chip, disabled && styles.dim]}>
              <Text style={styles.chipText}>{p}</Text>
            </Pressable>
          ))}
        </ScrollView>
      )}

      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          value={input}
          onChangeText={setInput}
          placeholder={remaining === 0 ? '대화 횟수를 모두 사용했어요' : '궁금한 스타일을 물어보세요'}
          maxLength={MAX_LENGTH}
          editable={remaining !== 0}
          onSubmitEditing={() => send(input)}
          returnKeyType="send"
          accessibilityLabel="메시지 입력"
        />
        <Pressable
          onPress={() => send(input)}
          disabled={disabled || !input.trim()}
          style={[styles.send, (disabled || !input.trim()) && styles.dim]}
          accessibilityRole="button"
          accessibilityLabel="전송"
        >
          <Text style={styles.sendText}>전송</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

function Bubble({ role, content, images = [] }: Pick<ChatMessage, 'role' | 'content'> & { images?: ChatMessage['images'] }) {
  const mine = role === 'USER';
  return (
    <View style={[styles.bubble, mine ? styles.user : styles.assistant]}>
      <Text style={[styles.bubbleText, mine && styles.userText]}>{content}</Text>
      {images.length > 0 && (
        <View style={styles.images}>
          {images.map((img) => (
            <StyleImage key={img.imageKey} imageKey={img.imageKey} label={img.label} size="small" />
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  back: { minWidth: 64, minHeight: 44, justifyContent: 'center' },
  backText: { fontSize: 16, color: colors.primary },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  turns: { minWidth: 64, textAlign: 'right', fontSize: 12, color: colors.subtext },
  list: { padding: 16, gap: 10 },
  bubble: { maxWidth: '85%', borderRadius: 16, paddingHorizontal: 14, paddingVertical: 10 },
  assistant: { alignSelf: 'flex-start', backgroundColor: colors.muted },
  user: { alignSelf: 'flex-end', backgroundColor: colors.primary },
  bubbleText: { fontSize: 15, lineHeight: 22, color: colors.text },
  userText: { color: colors.onPrimary },
  images: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 10 },
  typing: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  typingText: { color: colors.subtext },
  errorRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 6, gap: 8 },
  error: { flex: 1, color: colors.danger, fontSize: 13 },
  retry: { minHeight: 36, paddingHorizontal: 12, justifyContent: 'center', borderRadius: 8, borderWidth: 1, borderColor: colors.danger },
  retryText: { color: colors.danger, fontSize: 13, fontWeight: '600' },
  chipBar: { flexGrow: 0 }, // 없으면 가로 스크롤이 남은 세로 공간을 다 차지함
  chips: { paddingHorizontal: 12, paddingVertical: 8, gap: 8, alignItems: 'center' },
  chip: { borderWidth: 1, borderColor: colors.border, borderRadius: 999, paddingHorizontal: 14, minHeight: 36, justifyContent: 'center' },
  chipText: { fontSize: 13, color: colors.text },
  dim: { opacity: 0.4 },
  inputRow: { flexDirection: 'row', padding: 12, gap: 8, borderTopWidth: 1, borderTopColor: colors.border },
  input: { flex: 1, borderWidth: 1, borderColor: colors.border, borderRadius: 12, paddingHorizontal: 12, minHeight: 44, fontSize: 15 },
  send: { backgroundColor: colors.primary, borderRadius: 12, paddingHorizontal: 18, minHeight: 44, justifyContent: 'center' },
  sendText: { color: colors.onPrimary, fontWeight: '700' },
});
