package com.moda.session.service;

import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 세션별 분석 진행 상태와 처리 중인 사진(메모리 바이트)을 관리합니다.
 * <ul>
 *   <li>같은 세션에서 분석이 진행 중이면 새 요청은 {@code 409 ANALYSIS_IN_PROGRESS} (버튼 연타 방지)</li>
 *   <li>분석이 끝나면 성공·실패·예외와 관계없이 사진 바이트를 0으로 덮어써 폐기 (R-01)</li>
 *   <li>분석 도중 세션이 초기화(⑧)되면 즉시 폐기하고 {@code deletedPhotoCount}로 집계</li>
 * </ul>
 * MVP는 단일 서버 기준의 메모리 잠금입니다.
 */
@Component
public class AnalysisInProgressRegistry {

    private final ConcurrentHashMap<String, InFlight> inFlight = new ConcurrentHashMap<>();

    public static final class InFlight {
        private final String sessionId;
        private final byte[] bytes;

        private InFlight(String sessionId, byte[] bytes) {
            this.sessionId = sessionId;
            this.bytes = bytes;
        }
    }

    public InFlight acquire(String sessionId, byte[] bytes) {
        InFlight token = new InFlight(sessionId, bytes);
        if (inFlight.putIfAbsent(sessionId, token) != null) {
            wipe(bytes);
            throw new BusinessException(ErrorCode.ANALYSIS_IN_PROGRESS);
        }
        return token;
    }

    public void release(InFlight token) {
        inFlight.remove(token.sessionId, token);
        wipe(token.bytes);
    }

    /** 세션 초기화 시 진행 중인 사진을 폐기합니다. @return 폐기한 사진 수 (0 또는 1) */
    public int discard(String sessionId) {
        InFlight token = inFlight.remove(sessionId);
        if (token == null) {
            return 0;
        }
        wipe(token.bytes);
        return 1;
    }

    public boolean isInProgress(String sessionId) {
        return inFlight.containsKey(sessionId);
    }

    static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
