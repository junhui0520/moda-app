// src/screens/ChatScreen.tsx — A안 에디토리얼 챗봇 화면 (⑨ GET /chat/messages, ⑩ POST /chat/messages)

import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { isModaApiError } from '../api/client';
import { modaApi } from '../api/modaApi';
import type { ChatMessage } from '../api/types';
import type { RootStackParamList } from '../navigation/types';
import { useSessionStore } from '../store/useSessionStore';
import { colors, radius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Chat'>;

interface UiMessage extends ChatMessage {
  failed?: boolean;
  errorText?: string;
}

export function ChatScreen({ navigation }: Props) {
  const { sessionId, baseUrl, mockConfig, resetSessionLocal } = useSessionStore();

  const [loadingInit, setLoadingInit] = useState(true);
  const [initError, setInitError] = useState<string | null>(null);
  const [welcomeMessage, setWelcomeMessage] = useState<string>('');
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [suggestedPrompts, setSuggestedPrompts] = useState<string[]>([]);
  const [remainingTurns, setRemainingTurns] = useState<number>(20);
  const [maxTurns, setMaxTurns] = useState<number>(20);
  const [turnLimitBanner, setTurnLimitBanner] = useState<string | null>(null);

  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);

  const scrollRef = useRef<React.ComponentRef<typeof ScrollView> | null>(null);

  const scrollToBottom = () => {
    setTimeout(() => {
      scrollRef.current?.scrollToEnd({ animated: true });
    }, 60);
  };

  const handleSessionExpired = (message: string) => {
    Alert.alert('세션 만료', message, [
      {
        text: '처음으로',
        onPress: () => {
          resetSessionLocal();
          navigation.reset({ index: 0, routes: [{ name: 'Start' }] });
        },
      },
    ]);
  };

  const loadHistory = async () => {
    if (!sessionId) {
      handleSessionExpired('체험 세션을 찾을 수 없어요. 처음부터 다시 시작해 주세요.');
      return;
    }

    setLoadingInit(true);
    setInitError(null);
    try {
      const data = await modaApi.getChatMessages(sessionId, baseUrl);
      setWelcomeMessage(data.welcomeMessage);
      setMessages(data.messages);
      setSuggestedPrompts(data.suggestedPrompts);
      setRemainingTurns(data.remainingTurns);
      setMaxTurns(data.maxTurns);
      if (data.remainingTurns <= 0) {
        setTurnLimitBanner(`이번 체험에서 대화할 수 있는 횟수(${data.maxTurns}회)를 모두 사용했어요.`);
      }
      scrollToBottom();
    } catch (err) {
      if (isModaApiError(err)) {
        if (err.isSessionExpired()) {
          handleSessionExpired(err.message);
          return;
        }
        if (err.code === 'PROFILE_INPUT_REQUIRED' || err.code === 'ANALYSIS_INCOMPLETE') {
          const next = err.meta?.nextStep;
          Alert.alert('분석 필요', err.message, [
            {
              text: '이동하기',
              onPress: () => {
                if (next === 'PROFILE_INPUT' || err.code === 'PROFILE_INPUT_REQUIRED') {
                  navigation.navigate('ProfileInput');
                } else if (next === 'FACE_ANALYSIS') {
                  navigation.navigate('FaceCapture');
                } else {
                  navigation.navigate('BodyCapture');
                }
              },
            },
          ]);
          return;
        }
      }
      setInitError(err instanceof Error ? err.message : '챗봇을 불러오지 못했어요.');
    } finally {
      setLoadingInit(false);
    }
  };

  useEffect(() => {
    loadHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sendMessage = async (rawText: string, retryTempId?: string) => {
    if (!sessionId || sending) return;
    if (remainingTurns <= 0) return;

    const trimmed = rawText.trim();
    if (!trimmed) return;
    if (trimmed.length > 500) {
      Alert.alert('입력 길이 초과', '메시지는 500자 이하로 입력해 주세요.');
      return;
    }

    const tempId = retryTempId ?? `temp-${Date.now()}`;
    const optimisticMsg: UiMessage = {
      messageId: tempId,
      role: 'USER',
      content: trimmed,
      createdAt: new Date().toISOString(),
    };

    setSending(true);
    setMessages((prev) => {
      const filtered = retryTempId ? prev.filter((m) => m.messageId !== retryTempId) : prev;
      return [...filtered, optimisticMsg];
    });
    if (!retryTempId) {
      setInputText('');
    }
    scrollToBottom();

    try {
      const data = await modaApi.sendChatMessage(
        sessionId,
        { message: trimmed },
        mockConfig,
        baseUrl,
      );

      setMessages((prev) => [
        ...prev.filter((m) => m.messageId !== tempId),
        data.userMessage,
        data.assistantMessage,
      ]);
      setSuggestedPrompts(data.suggestedPrompts);
      setRemainingTurns(data.remainingTurns);
      setMaxTurns(data.maxTurns);
      if (data.remainingTurns <= 0) {
        setTurnLimitBanner(`이번 체험에서 대화할 수 있는 횟수(${data.maxTurns}회)를 모두 사용했어요.`);
      }
      scrollToBottom();
    } catch (err) {
      if (isModaApiError(err)) {
        if (err.isSessionExpired()) {
          handleSessionExpired(err.message);
          return;
        }
        if (err.code === 'CHAT_TURN_LIMIT_EXCEEDED') {
          setMessages((prev) => prev.filter((m) => m.messageId !== tempId));
          setRemainingTurns(0);
          setSuggestedPrompts([]);
          setTurnLimitBanner(err.message);
          return;
        }
        if (err.code === 'CHAT_SERVER_UNAVAILABLE' || err.code === 'CHAT_TIMEOUT') {
          setMessages((prev) =>
            prev.map((m) =>
              m.messageId === tempId ? { ...m, failed: true, errorText: err.message } : m,
            ),
          );
          setInputText(trimmed);
          scrollToBottom();
          return;
        }
      }

      setMessages((prev) =>
        prev.map((m) =>
          m.messageId === tempId
            ? {
                ...m,
                failed: true,
                errorText: err instanceof Error ? err.message : '전송에 실패했어요.',
              }
            : m,
        ),
      );
      setInputText(trimmed);
    } finally {
      setSending(false);
    }
  };

  const isDisabled = sending || remainingTurns <= 0;

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={24} color={colors.text} />
        </Pressable>
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle}>MODA 챗봇</Text>
          <Text style={styles.headerSub}>맞춤 스타일 상담</Text>
        </View>
        <View style={styles.turnBadge}>
          <Text style={styles.turnBadgeText}>
            {remainingTurns} / {maxTurns}
          </Text>
        </View>
      </View>

      {loadingInit ? (
        <View style={styles.centerBox}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.loadingText}>챗봇을 불러오고 있어요...</Text>
        </View>
      ) : initError ? (
        <View style={styles.centerBox}>
          <Text style={styles.errorText}>{initError}</Text>
          <Pressable style={styles.retryInitBtn} onPress={loadHistory}>
            <Text style={styles.retryInitText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
        <KeyboardAvoidingView
          style={{ flex: 1 }}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
          <ScrollView
            ref={scrollRef}
            contentContainerStyle={styles.chatList}
            keyboardShouldPersistTaps="handled"
          >
            {welcomeMessage ? (
              <View style={styles.assistantRow}>
                <View style={styles.botBadge}>
                  <Text style={styles.botBadgeText}>M</Text>
                </View>
                <View style={styles.assistantBubble}>
                  <Text style={styles.assistantText}>{welcomeMessage}</Text>
                </View>
              </View>
            ) : null}

            {messages.map((msg) =>
              msg.role === 'USER' ? (
                <View key={msg.messageId} style={styles.userRow}>
                  <View style={[styles.userBubble, msg.failed && styles.userBubbleFailed]}>
                    <Text style={styles.userText}>{msg.content}</Text>
                  </View>
                  {msg.failed ? (
                    <View style={styles.failedBar}>
                      <Text style={styles.failedText}>
                        {msg.errorText || '전송 실패 (횟수 차감 없음)'}
                      </Text>
                      <Pressable
                        style={styles.resendBtn}
                        onPress={() => sendMessage(msg.content, msg.messageId)}
                        disabled={sending}
                      >
                        <Text style={styles.resendText}>다시 보내기</Text>
                      </Pressable>
                    </View>
                  ) : null}
                </View>
              ) : (
                <View key={msg.messageId} style={styles.assistantRow}>
                  <View style={styles.botBadge}>
                    <Text style={styles.botBadgeText}>M</Text>
                  </View>
                  <View style={styles.assistantBubble}>
                    <Text style={styles.assistantText}>{msg.content}</Text>
                  </View>
                </View>
              ),
            )}

            {sending ? (
              <View style={styles.assistantRow}>
                <View style={styles.botBadge}>
                  <Text style={styles.botBadgeText}>M</Text>
                </View>
                <View style={[styles.assistantBubble, styles.typingBubble]}>
                  <ActivityIndicator size="small" color={colors.primary} />
                  <Text style={styles.typingText}>답변 작성 중...</Text>
                </View>
              </View>
            ) : null}
          </ScrollView>

          {turnLimitBanner ? (
            <View style={styles.limitBanner}>
              <Text style={styles.limitBannerText}>{turnLimitBanner}</Text>
            </View>
          ) : null}

          {suggestedPrompts.length > 0 && remainingTurns > 0 ? (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.promptsBar}
              style={styles.promptsScroll}
            >
              {suggestedPrompts.map((prompt, idx) => (
                <Pressable
                  key={`${prompt}-${idx}`}
                  style={[styles.promptChip, isDisabled && { opacity: 0.4 }]}
                  onPress={() => sendMessage(prompt)}
                  disabled={isDisabled}
                >
                  <Text style={styles.promptChipText}>{prompt}</Text>
                </Pressable>
              ))}
            </ScrollView>
          ) : null}

          <View style={styles.inputBar}>
            <TextInput
              value={inputText}
              onChangeText={setInputText}
              placeholder={
                remainingTurns <= 0
                  ? '대화 횟수를 모두 사용했어요'
                  : '코디나 헤어 연출법을 물어보세요'
              }
              placeholderTextColor={colors.textMuted}
              editable={!isDisabled}
              maxLength={500}
              multiline
              style={[styles.textInput, remainingTurns <= 0 && styles.textInputDisabled]}
            />
            <Pressable
              style={[
                styles.sendBtn,
                (isDisabled || !inputText.trim()) && styles.sendBtnDisabled,
              ]}
              onPress={() => sendMessage(inputText)}
              disabled={isDisabled || !inputText.trim()}
            >
              <Ionicons name="arrow-up" size={19} color="#FFFFFF" />
            </Pressable>
          </View>
        </KeyboardAvoidingView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  backBtn: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 15.5,
    fontWeight: '900',
    letterSpacing: 0.5,
    color: colors.text,
  },
  headerSub: {
    fontSize: 11.5,
    color: colors.textMuted,
    marginTop: 1,
  },
  turnBadge: {
    borderWidth: 1,
    borderColor: colors.borderStrong,
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: radius.xs,
  },
  turnBadgeText: {
    fontSize: 11,
    fontWeight: '800',
    color: colors.text,
  },
  centerBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  loadingText: {
    marginTop: 12,
    fontSize: 13.5,
    color: colors.textSecondary,
  },
  errorText: {
    fontSize: 14,
    color: colors.danger,
    textAlign: 'center',
    marginBottom: 12,
  },
  retryInitBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: radius.sm,
  },
  retryInitText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  chatList: {
    padding: 16,
    paddingBottom: 20,
    gap: 14,
  },
  assistantRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    maxWidth: '90%',
  },
  botBadge: {
    width: 26,
    height: 26,
    borderRadius: radius.xs,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  botBadgeText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '900',
  },
  assistantBubble: {
    flexShrink: 1,
    backgroundColor: colors.surfaceMuted,
    borderRadius: radius.md,
    borderTopLeftRadius: 2,
    paddingHorizontal: 14,
    paddingVertical: 11,
  },
  assistantText: {
    fontSize: 14,
    color: colors.text,
    lineHeight: 21,
  },
  typingBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  typingText: {
    fontSize: 13,
    color: colors.textSecondary,
  },
  userRow: {
    alignItems: 'flex-end',
  },
  userBubble: {
    maxWidth: '82%',
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    borderTopRightRadius: 2,
    paddingHorizontal: 14,
    paddingVertical: 11,
  },
  userBubbleFailed: {
    backgroundColor: '#7F1D1D',
  },
  userText: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 20,
  },
  failedBar: {
    marginTop: 6,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  failedText: {
    fontSize: 11.5,
    color: colors.danger,
  },
  resendBtn: {
    borderWidth: 1,
    borderColor: colors.dangerBorder,
    backgroundColor: colors.dangerBg,
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: radius.xs,
  },
  resendText: {
    fontSize: 11.5,
    fontWeight: '800',
    color: colors.danger,
  },
  limitBanner: {
    backgroundColor: colors.surfaceMuted,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  limitBannerText: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textSecondary,
    textAlign: 'center',
  },
  promptsScroll: {
    maxHeight: 46,
    backgroundColor: colors.bg,
  },
  promptsBar: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    gap: 8,
    alignItems: 'center',
  },
  promptChip: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: radius.xs,
  },
  promptChipText: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.text,
  },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  textInput: {
    flex: 1,
    maxHeight: 96,
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    paddingHorizontal: 13,
    paddingVertical: 10,
    fontSize: 14,
    color: colors.text,
  },
  textInputDisabled: {
    opacity: 0.5,
  },
  sendBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.sm,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendBtnDisabled: {
    opacity: 0.3,
  },
});
