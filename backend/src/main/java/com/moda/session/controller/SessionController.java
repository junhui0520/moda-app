package com.moda.session.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.ai.AnalysisOptions;
import com.moda.global.response.ApiResponse;
import com.moda.session.dto.AnalysisDetailResponses.AnalysisDetailData;
import com.moda.session.dto.SessionRequests.GuestSessionRequest;
import com.moda.session.dto.SessionRequests.ProfileInputRequest;
import com.moda.session.dto.SessionRequests.ProfilePatchRequest;
import com.moda.session.dto.SessionResponses.BodyAnalysisData;
import com.moda.session.dto.SessionResponses.FaceAnalysisData;
import com.moda.session.dto.SessionResponses.GuestSessionData;
import com.moda.session.dto.SessionResponses.ProfileInputData;
import com.moda.session.dto.SessionResponses.ProfilePatchData;
import com.moda.session.dto.SessionResponses.ResultData;
import com.moda.session.dto.SessionResponses.SessionDeleteData;
import com.moda.session.service.GuestSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 게스트 세션 API ①~⑧ + 실시간 분석 ③-L·④-L (docs/api-spec.md 5장). */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Session", description = "게스트 세션 · 얼굴/전신 분석(사진 ③④ · 실시간 ③-L ④-L) · 결과 · 수동 보정 (API ①~⑧)")
public class SessionController {

    private final GuestSessionService service;

    public SessionController(GuestSessionService service) {
        this.service = service;
    }

    @Operation(summary = "① 게스트 세션 생성", description = "photoConsent=true 필수. 201 Created")
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<GuestSessionData>> createGuestSession(
            @Valid @RequestBody(required = false) GuestSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request)));
    }

    @Operation(summary = "② 기본 정보 입력", description = "전체 덮어쓰기. AI 체형 분석 후 heightCm가 바뀌면 체형 결과 초기화(bodyAnalysisReset=true)")
    @PostMapping("/{sessionId}/profile-input")
    public ApiResponse<ProfileInputData> saveProfileInput(@PathVariable String sessionId,
                                                          @Valid @RequestBody ProfileInputRequest request) {
        return ApiResponse.success(service.saveProfileInput(sessionId, request));
    }

    @Operation(summary = "③ 얼굴 사진 분석", description = "multipart 'image' 1장(JPG/PNG, 10MB 이하). 분석 불가 사진은 200 + usable=false")
    @PostMapping(value = "/{sessionId}/analyze/face", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public ApiResponse<FaceAnalysisData> analyzeFace(
            @PathVariable String sessionId,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @Parameter(description = "Mock: 얼굴형 Key (기본 oval)") @RequestHeader(value = AnalysisOptions.H_FACE_SHAPE, required = false) String mockFaceShape,
            @Parameter(description = "Mock: true면 usable=false") @RequestHeader(value = AnalysisOptions.H_UNUSABLE, required = false) String mockUnusable,
            @Parameter(description = "Mock: true면 borderline") @RequestHeader(value = AnalysisOptions.H_BORDERLINE, required = false) String mockBorderline,
            @Parameter(description = "Mock: 응답 지연 0~10000ms (기본 800)") @RequestHeader(value = AnalysisOptions.H_DELAY_MS, required = false) String mockDelayMs) {
        AnalysisOptions options = new AnalysisOptions(mockFaceShape, null, mockUnusable, mockBorderline, null, mockDelayMs);
        return ApiResponse.success(service.analyzeFace(sessionId, image, options));
    }

    @Operation(summary = "④ 전신 사진 분석", description = "세션의 heightCm를 서버가 자동 사용. 기본 정보 미입력 시 409 PROFILE_INPUT_REQUIRED")
    @PostMapping(value = "/{sessionId}/analyze/body", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public ApiResponse<BodyAnalysisData> analyzeBody(
            @PathVariable String sessionId,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @Parameter(description = "Mock: 체형 Key (기본 standard)") @RequestHeader(value = AnalysisOptions.H_BODY_TYPE, required = false) String mockBodyType,
            @Parameter(description = "Mock: true면 usable=false") @RequestHeader(value = AnalysisOptions.H_UNUSABLE, required = false) String mockUnusable,
            @Parameter(description = "Mock: true면 borderline") @RequestHeader(value = AnalysisOptions.H_BORDERLINE, required = false) String mockBorderline,
            @Parameter(description = "Mock: true면 필수 필드만") @RequestHeader(value = AnalysisOptions.H_BODY_MINIMAL, required = false) String mockBodyMinimal,
            @Parameter(description = "Mock: 응답 지연 0~10000ms (기본 800)") @RequestHeader(value = AnalysisOptions.H_DELAY_MS, required = false) String mockDelayMs) {
        AnalysisOptions options = new AnalysisOptions(null, mockBodyType, mockUnusable, mockBorderline, mockBodyMinimal, mockDelayMs);
        return ApiResponse.success(service.analyzeBody(sessionId, image, options));
    }

    @Operation(summary = "③-L 얼굴 실시간 분석",
            description = "앱 WebView 측정 페이지({AI}/live/live.html?mode=face)가 준 payload(JSON: rounds, quality)를 그대로 전송. "
                    + "사진 없음. 응답·에러·Mock 헤더는 ③과 같음 (사진 관련 에러 제외, rounds 형식 오류는 400)")
    @PostMapping(value = "/{sessionId}/analyze/face/live", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<FaceAnalysisData> analyzeFaceLive(
            @PathVariable String sessionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "측정 페이지 payload: {\"rounds\": [[{프레임 측정값}, ...], ...], \"quality\": {...}}")
            @RequestBody JsonNode measurement,
            @Parameter(description = "Mock: 얼굴형 Key (기본 oval)") @RequestHeader(value = AnalysisOptions.H_FACE_SHAPE, required = false) String mockFaceShape,
            @Parameter(description = "Mock: true면 usable=false") @RequestHeader(value = AnalysisOptions.H_UNUSABLE, required = false) String mockUnusable,
            @Parameter(description = "Mock: true면 borderline") @RequestHeader(value = AnalysisOptions.H_BORDERLINE, required = false) String mockBorderline,
            @Parameter(description = "Mock: 응답 지연 0~10000ms (기본 800)") @RequestHeader(value = AnalysisOptions.H_DELAY_MS, required = false) String mockDelayMs) {
        AnalysisOptions options = new AnalysisOptions(mockFaceShape, null, mockUnusable, mockBorderline, null, mockDelayMs);
        return ApiResponse.success(service.analyzeFaceLive(sessionId, measurement, options));
    }

    @Operation(summary = "④-L 체형 실시간 분석",
            description = "측정 페이지(mode=body) payload를 그대로 전송. 세션 heightCm를 서버가 붙여 AI로 전달(요청의 height_cm은 무시). "
                    + "기본 정보 미입력 시 409 PROFILE_INPUT_REQUIRED. 응답·에러·Mock 헤더는 ④와 같음")
    @PostMapping(value = "/{sessionId}/analyze/body/live", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BodyAnalysisData> analyzeBodyLive(
            @PathVariable String sessionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "측정 페이지 payload: {\"rounds\": [[{프레임 측정값}, ...], ...], \"quality\": {...}}")
            @RequestBody JsonNode measurement,
            @Parameter(description = "Mock: 체형 Key (기본 standard)") @RequestHeader(value = AnalysisOptions.H_BODY_TYPE, required = false) String mockBodyType,
            @Parameter(description = "Mock: true면 usable=false") @RequestHeader(value = AnalysisOptions.H_UNUSABLE, required = false) String mockUnusable,
            @Parameter(description = "Mock: true면 borderline") @RequestHeader(value = AnalysisOptions.H_BORDERLINE, required = false) String mockBorderline,
            @Parameter(description = "Mock: true면 필수 필드만") @RequestHeader(value = AnalysisOptions.H_BODY_MINIMAL, required = false) String mockBodyMinimal,
            @Parameter(description = "Mock: 응답 지연 0~10000ms (기본 800)") @RequestHeader(value = AnalysisOptions.H_DELAY_MS, required = false) String mockDelayMs) {
        AnalysisOptions options = new AnalysisOptions(null, mockBodyType, mockUnusable, mockBorderline, mockBodyMinimal, mockDelayMs);
        return ApiResponse.success(service.analyzeBodyLive(sessionId, measurement, options));
    }

    @Operation(summary = "⑤ 최종 결과 조회", description = "맞춤 유형 + 추천 헤어 2 + 추천 패션 3")
    @GetMapping("/{sessionId}/result")
    public ApiResponse<ResultData> getResult(@PathVariable String sessionId) {
        return ApiResponse.success(service.getResult(sessionId));
    }

    @Operation(summary = "⑥ 분석 상세보기", description = "미분석 항목도 status=NOT_ANALYZED로 항상 200")
    @GetMapping("/{sessionId}/analysis-detail")
    public ApiResponse<AnalysisDetailData> getAnalysisDetail(@PathVariable String sessionId) {
        return ApiResponse.success(service.getAnalysisDetail(sessionId));
    }

    @Operation(summary = "⑦ 수동 보정 및 결과 재산출", description = "보낸 필드만 변경. 결과 산출 가능하면 result 포함")
    @PatchMapping("/{sessionId}/profile")
    public ApiResponse<ProfilePatchData> patchProfile(@PathVariable String sessionId,
                                                      @Valid @RequestBody(required = false) ProfilePatchRequest request) {
        return ApiResponse.success(service.patchProfile(sessionId, request));
    }

    @Operation(summary = "⑧ 세션 초기화", description = "멱등: 이미 없거나 만료된 세션도 200 (alreadyDeleted=true)")
    @DeleteMapping("/{sessionId}")
    public ApiResponse<SessionDeleteData> deleteSession(@PathVariable String sessionId) {
        return ApiResponse.success(service.delete(sessionId));
    }
}
