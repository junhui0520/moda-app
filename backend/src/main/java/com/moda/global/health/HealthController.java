package com.moda.global.health;

import com.moda.global.config.ModaProperties;
import com.moda.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클라우드 헬스체크 (Railway·Render 등). 인증·세션 없이 항상 {@code 200 OK}.
 * <pre>{"success": true, "data": {"status": "ok", "aiMode": "mock", "chatMode": "mock"}}</pre>
 * 외부 AI·LLM 서버의 상태는 확인하지 않으므로(장애 시에도 앱은 Mock 전환·재시도로 응답 가능), 앱 프로세스 생존만 나타냅니다.
 */
@RestController
@Tag(name = "Health", description = "클라우드 헬스체크")
public class HealthController {

    private final ModaProperties properties;

    public HealthController(ModaProperties properties) {
        this.properties = properties;
    }

    public record HealthData(String status, String aiMode, String chatMode) {
    }

    @Operation(summary = "헬스체크", description = "인증·세션 없이 200 OK. 현재 AI/챗봇 모드(mock | external) 표시")
    @GetMapping("/api/v1/health")
    public ApiResponse<HealthData> health() {
        return ApiResponse.success(new HealthData(
                "ok",
                properties.ai().mode().name().toLowerCase(Locale.ROOT),
                properties.chat().mode().name().toLowerCase(Locale.ROOT)));
    }
}
