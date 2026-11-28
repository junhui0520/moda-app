package com.moda.session.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.ai.AnalysisClient;
import com.moda.ai.AnalysisImage;
import com.moda.ai.AnalysisOptions;
import com.moda.ai.model.AnalysisValues.WarningValue;
import com.moda.ai.model.BodyAnalysisResult;
import com.moda.ai.model.FaceAnalysisResult;
import com.moda.common.enums.AnalysisType;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import com.moda.global.config.ModaProperties;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import com.moda.global.time.KstTime;
import com.moda.recommendation.RecommendationInput;
import com.moda.recommendation.StyleRecommendation;
import com.moda.recommendation.StyleRecommendationService;
import com.moda.seed.catalog.SeedCatalog;
import com.moda.session.domain.GuestSession;
import com.moda.session.dto.AnalysisDetailResponses.AnalysisDetailData;
import com.moda.session.dto.AnalysisDetailResponses.EditableOptions;
import com.moda.session.dto.SessionRequests.GuestSessionRequest;
import com.moda.session.dto.SessionRequests.ProfileInputRequest;
import com.moda.session.dto.SessionRequests.ProfilePatchRequest;
import com.moda.session.dto.SessionResponses.BodyAnalysisData;
import com.moda.session.dto.SessionResponses.FaceAnalysisData;
import com.moda.session.dto.SessionResponses.GuestSessionData;
import com.moda.session.dto.SessionResponses.ProfileInputData;
import com.moda.session.dto.SessionResponses.ProfilePatchData;
import com.moda.session.dto.SessionResponses.ResultBasis;
import com.moda.session.dto.SessionResponses.ResultData;
import com.moda.session.dto.SessionResponses.SessionDeleteData;
import com.moda.session.repository.GuestSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 게스트 세션 API ①~⑧ 비즈니스 로직 (docs/api-spec.md 5장).
 * <p>세션 TTL은 쓰기 요청(POST/PATCH) 성공 시마다 {@code moda.session.ttl-minutes}만큼 연장합니다.
 * AI 분석 호출(최대 15~20초)은 DB 트랜잭션 밖에서 수행하고, 결과 반영만 짧은 트랜잭션으로 처리합니다.
 */
@Service
public class GuestSessionService {

    /** 결과 화면 하단 고정 안내 문구 (style-rules.md 7장, api-spec.md 3.11). */
    public static final String DISCLAIMER = "분석 결과는 스타일 참고용이며, 언제든 [분석 상세보기]에서 직접 수정할 수 있어요.";
    static final String PATCH_EMPTY_MESSAGE = "수정할 항목을 1개 이상 보내 주세요.";

    private final GuestSessionRepository repository;
    private final StyleRecommendationService recommendationService;
    private final AnalysisClient analysisClient;
    private final AnalysisImageReader imageReader;
    private final AnalysisInProgressRegistry inProgressRegistry;
    private final SessionViewMapper mapper;
    private final SeedCatalog catalog;
    private final Clock clock;
    private final Duration ttl;
    private final int ttlMinutes;
    private final TransactionTemplate tx;
    private final List<SessionDataPurger> purgers;

    public GuestSessionService(GuestSessionRepository repository, StyleRecommendationService recommendationService,
                               AnalysisClient analysisClient, AnalysisImageReader imageReader,
                               AnalysisInProgressRegistry inProgressRegistry, SessionViewMapper mapper, SeedCatalog catalog,
                               ModaProperties properties, Clock clock, PlatformTransactionManager transactionManager,
                               List<SessionDataPurger> purgers) {
        this.purgers = List.copyOf(purgers);
        this.repository = repository;
        this.recommendationService = recommendationService;
        this.analysisClient = analysisClient;
        this.imageReader = imageReader;
        this.inProgressRegistry = inProgressRegistry;
        this.mapper = mapper;
        this.catalog = catalog;
        this.clock = clock;
        this.ttlMinutes = properties.session().ttlMinutes();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ ① 게스트 세션 생성

    @Transactional
    public GuestSessionData create(GuestSessionRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.photoConsent())) {
            throw new BusinessException(ErrorCode.PHOTO_CONSENT_REQUIRED);
        }
        Instant now = now();
        String deviceLabel = request.deviceLabel() == null || request.deviceLabel().isBlank() ? null : request.deviceLabel().strip();
        GuestSession session = GuestSession.create(UUID.randomUUID().toString(), true, deviceLabel, now, now.plus(ttl));
        repository.save(session);
        return new GuestSessionData(session.getSessionId(), KstTime.toOffset(now), KstTime.toOffset(session.getExpiresAt()),
                ttlMinutes, mapper.progress(session), mapper.nextStep(session));
    }

    // ------------------------------------------------------------------ ② 기본 정보 입력

    @Transactional
    public ProfileInputData saveProfileInput(String sessionId, ProfileInputRequest request) {
        GuestSession session = requireActive(sessionId);
        boolean bodyReset = session.updateProfile(request.age(), request.heightCm(), request.weightKg(),
                HairThickness.valueOf(request.hairThickness()), HairCurl.valueOf(request.hairCurl()),
                HairDensity.valueOf(request.hairDensity()));
        touch(session);
        return new ProfileInputData(sessionId, mapper.profileInput(session), bodyReset, mapper.progress(session),
                mapper.nextStep(session), KstTime.toOffset(session.getExpiresAt()));
    }

    // ------------------------------------------------------------------ ③ 얼굴 분석

    public FaceAnalysisData analyzeFace(String sessionId, MultipartFile file, AnalysisOptions options) {
        requireActiveSnapshot(sessionId);
        AnalysisImage image = imageReader.read(file, AnalysisType.FACE);
        AnalysisInProgressRegistry.InFlight token = inProgressRegistry.acquire(sessionId, image.bytes());
        try {
            FaceAnalysisResult result = analysisClient.analyzeFace(image, options);
            return tx.execute(status -> applyFace(sessionId, result));
        } finally {
            inProgressRegistry.release(token);                                      // 사진 바이트 즉시 폐기 (R-01)
        }
    }

    private FaceAnalysisData applyFace(String sessionId, FaceAnalysisResult r) {
        GuestSession session = requireActive(sessionId);
        Instant now = now();
        List<WarningValue> warnings = mapper.normalizeWarnings(r.warningCodes(), r.usable(), r.borderline(), AnalysisType.FACE);
        if (r.usable()) {
            session.applyFaceAnalysis(r.faceShape(), r.confidence(), r.borderline(), r.reason(), r.candidates(), warnings,
                    r.features(), now);
        }
        // usable=false: 이전 결과·progress 유지, 경고와 재촬영 안내만 반환
        touch(session, now);
        return new FaceAnalysisData(sessionId, AnalysisType.FACE, r.usable(), !r.usable(),
                r.usable() ? catalog.faceLabel(r.faceShape()) : null,
                r.usable() ? r.confidence() : null,
                r.usable() ? r.borderline() : null,
                mapper.warnings(warnings), mapper.progress(session), mapper.nextStep(session),
                KstTime.toOffset(now), KstTime.toOffset(session.getExpiresAt()));
    }

    // ------------------------------------------------------------------ ④ 전신 분석

    public BodyAnalysisData analyzeBody(String sessionId, MultipartFile file, AnalysisOptions options) {
        GuestSession snapshot = requireActiveSnapshot(sessionId);
        if (!snapshot.hasProfile()) {
            throw new BusinessException(ErrorCode.PROFILE_INPUT_REQUIRED);
        }
        int heightCm = snapshot.getHeightCm();                                      // 세션에 저장된 키를 서버가 자동 사용
        AnalysisImage image = imageReader.read(file, AnalysisType.BODY);
        AnalysisInProgressRegistry.InFlight token = inProgressRegistry.acquire(sessionId, image.bytes());
        try {
            BodyAnalysisResult result = analysisClient.analyzeBody(image, heightCm, options);
            return tx.execute(status -> applyBody(sessionId, heightCm, result));
        } finally {
            inProgressRegistry.release(token);
        }
    }

    private BodyAnalysisData applyBody(String sessionId, int heightCmUsed, BodyAnalysisResult r) {
        GuestSession session = requireActive(sessionId);
        Instant now = now();
        List<WarningValue> warnings = mapper.normalizeWarnings(r.warningCodes(), r.usable(), r.borderline(), AnalysisType.BODY);
        if (r.usable()) {
            session.applyBodyAnalysis(r.bodyType(), r.confidence(), r.borderline(), r.candidates(), warnings, r.features(),
                    r.compareToSizeKorea(), heightCmUsed, now);
        }
        touch(session, now);
        return new BodyAnalysisData(sessionId, AnalysisType.BODY, r.usable(), !r.usable(),
                r.usable() ? catalog.bodyLabel(r.bodyType()) : null,
                r.usable() ? r.confidence() : null,
                r.usable() ? r.borderline() : null,
                heightCmUsed, mapper.warnings(warnings), mapper.progress(session), mapper.nextStep(session),
                KstTime.toOffset(now), KstTime.toOffset(session.getExpiresAt()));
    }

    // ------------------------------------------------------------------ ③-L · ④-L 실시간 분석 (ai/LIVE_INTEGRATION.md)

    /**
     * ③-L 얼굴 실시간 분석: 휴대폰 측정 페이지가 잰 값(JSON)만 받습니다 (사진 없음, R-01에 더 유리).
     * 응답·결과 반영·TTL 연장·분석 잠금은 ③과 같습니다.
     */
    public FaceAnalysisData analyzeFaceLive(String sessionId, JsonNode measurement, AnalysisOptions options) {
        requireActiveSnapshot(sessionId);
        requireMeasurement(measurement);
        AnalysisInProgressRegistry.InFlight token = inProgressRegistry.acquire(sessionId, new byte[0]);   // 사진 없음
        try {
            FaceAnalysisResult result = analysisClient.analyzeFaceLive(measurement, UUID.randomUUID().toString(), options);
            return tx.execute(status -> applyFace(sessionId, result));
        } finally {
            inProgressRegistry.release(token);
        }
    }

    /** ④-L 체형 실시간 분석: 세션의 {@code heightCm}을 서버가 붙여 전달합니다. 응답은 ④와 같습니다. */
    public BodyAnalysisData analyzeBodyLive(String sessionId, JsonNode measurement, AnalysisOptions options) {
        GuestSession snapshot = requireActiveSnapshot(sessionId);
        if (!snapshot.hasProfile()) {
            throw new BusinessException(ErrorCode.PROFILE_INPUT_REQUIRED);
        }
        requireMeasurement(measurement);
        int heightCm = snapshot.getHeightCm();
        AnalysisInProgressRegistry.InFlight token = inProgressRegistry.acquire(sessionId, new byte[0]);
        try {
            BodyAnalysisResult result = analysisClient.analyzeBodyLive(measurement, heightCm, UUID.randomUUID().toString(), options);
            return tx.execute(status -> applyBody(sessionId, heightCm, result));
        } finally {
            inProgressRegistry.release(token);
        }
    }

    /** 측정 페이지는 회차 1~3, 회차당 좋은 프레임 5~6장을 보냄. 비정상적으로 큰 요청은 AI로 넘기지 않음. */
    static final int MAX_LIVE_ROUNDS = 5;
    static final int MAX_LIVE_FRAMES_PER_ROUND = 60;
    static final String MEASUREMENT_REQUIRED = "측정값(rounds)이 필요해요.";

    /**
     * {@code rounds}: 비어 있지 않은 배열, 각 회차는 프레임(객체) 배열. 형식이 틀리면 AI 422(→ 502) 대신 {@code 400 INVALID_REQUEST}.
     * 프레임 안의 측정값 판정(부족·이상치)은 AI가 하며, 부족하면 200 + {@code usable:false} + {@code LIVE_NOT_ENOUGH_FRAMES}.
     */
    static void requireMeasurement(JsonNode m) {
        JsonNode rounds = m == null ? null : m.path("rounds");
        if (rounds == null || !rounds.isArray() || rounds.isEmpty()) {
            throw BusinessException.invalidField("rounds", MEASUREMENT_REQUIRED, null);
        }
        if (rounds.size() > MAX_LIVE_ROUNDS) {
            throw BusinessException.invalidField("rounds", "회차는 최대 " + MAX_LIVE_ROUNDS + "개까지 보낼 수 있어요.", rounds.size());
        }
        for (int i = 0; i < rounds.size(); i++) {
            JsonNode round = rounds.get(i);
            if (!round.isArray()) {
                throw BusinessException.invalidField("rounds[" + i + "]", "회차는 프레임 측정값 배열이어야 해요.", null);
            }
            if (round.size() > MAX_LIVE_FRAMES_PER_ROUND) {
                throw BusinessException.invalidField("rounds[" + i + "]",
                        "회차당 프레임은 최대 " + MAX_LIVE_FRAMES_PER_ROUND + "개까지 보낼 수 있어요.", round.size());
            }
            for (JsonNode frame : round) {
                if (!frame.isObject()) {
                    throw BusinessException.invalidField("rounds[" + i + "]", "프레임 측정값은 객체여야 해요.", null);
                }
            }
        }
    }

    // ------------------------------------------------------------------ ⑤ 최종 결과

    @Transactional(readOnly = true)
    public ResultData getResult(String sessionId) {
        GuestSession session = requireActive(sessionId);
        requireResultReady(session);
        return buildResult(session);
    }

    /**
     * 결과 산출 선행 조건 (⑤·⑨·⑩ 공통): 기본 정보 → {@code 409 PROFILE_INPUT_REQUIRED},
     * 얼굴·체형 미확정 → {@code 409 ANALYSIS_INCOMPLETE} ({@code meta.missing}, {@code meta.nextStep}).
     */
    public void requireResultReady(GuestSession session) {
        if (!session.hasProfile()) {
            throw new BusinessException(ErrorCode.PROFILE_INPUT_REQUIRED);
        }
        if (session.getFaceResult() == null || session.getBodyResult() == null) {
            List<String> missing = new ArrayList<>();
            if (session.getFaceResult() == null) {
                missing.add(AnalysisType.FACE.name());
            }
            if (session.getBodyResult() == null) {
                missing.add(AnalysisType.BODY.name());
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("missing", missing);
            meta.put("nextStep", mapper.nextStep(session));
            throw BusinessException.withMeta(ErrorCode.ANALYSIS_INCOMPLETE, meta);
        }
    }

    // ------------------------------------------------------------------ ⑥ 분석 상세보기

    @Transactional(readOnly = true)
    public AnalysisDetailData getAnalysisDetail(String sessionId) {
        GuestSession session = requireActive(sessionId);
        return new AnalysisDetailData(sessionId, mapper.faceDetail(session), mapper.bodyDetail(session),
                mapper.profileInput(session), new EditableOptions(mapper.faceOptions(), mapper.bodyOptions()));
    }

    // ------------------------------------------------------------------ ⑦ 수동 보정 + 재산출

    @Transactional
    public ProfilePatchData patchProfile(String sessionId, ProfilePatchRequest request) {
        if (request == null || request.noFieldPresent()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, PATCH_EMPTY_MESSAGE);
        }
        GuestSession session = requireActive(sessionId);
        if (request.containsHairField() && !session.hasProfile()) {
            throw new BusinessException(ErrorCode.PROFILE_INPUT_REQUIRED);
        }
        List<String> changed = new ArrayList<>();
        if (request.faceShape() != null && session.correctFace(FaceShape.fromKey(request.faceShape()))) {
            changed.add("faceShape");
        }
        if (request.bodyType() != null && session.correctBody(BodyType.fromKey(request.bodyType()))) {
            changed.add("bodyType");
        }
        if (request.hairThickness() != null && session.changeHairThickness(HairThickness.valueOf(request.hairThickness()))) {
            changed.add("hairThickness");
        }
        if (request.hairCurl() != null && session.changeHairCurl(HairCurl.valueOf(request.hairCurl()))) {
            changed.add("hairCurl");
        }
        if (request.hairDensity() != null && session.changeHairDensity(HairDensity.valueOf(request.hairDensity()))) {
            changed.add("hairDensity");
        }
        touch(session);
        ResultData result = isResultReady(session) ? buildResult(session) : null;
        return new ProfilePatchData(sessionId, List.copyOf(changed), mapper.progress(session), mapper.nextStep(session),
                KstTime.toOffset(session.getExpiresAt()), result);
    }

    // ------------------------------------------------------------------ ⑧ 세션 초기화 (멱등)

    @Transactional
    public SessionDeleteData delete(String sessionId) {
        Instant now = now();
        GuestSession session = repository.findById(sessionId).orElse(null);
        boolean alreadyDeleted = session == null || session.isExpired(now);
        purgers.forEach(p -> p.purgeSession(sessionId));                           // 챗봇 대화 내역 등 하위 데이터 즉시 파기
        if (session != null) {
            repository.delete(session);
        }
        int deletedPhotos = inProgressRegistry.discard(sessionId);                  // 분석 도중 초기화 시 처리 중인 사진 폐기
        return new SessionDeleteData(sessionId, true, alreadyDeleted, deletedPhotos, KstTime.toOffset(now));
    }

    // ------------------------------------------------------------------ helpers

    /** 세션 조회: 없으면 404, 만료면 410. */
    public GuestSession requireActive(String sessionId) {
        GuestSession session = repository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (session.isExpired(now())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        return session;
    }

    /** 트랜잭션 밖에서 분석 전 사전 검사용으로 조회 (반환 엔티티는 수정하지 않음). */
    private GuestSession requireActiveSnapshot(String sessionId) {
        return requireActive(sessionId);
    }

    private boolean isResultReady(GuestSession s) {
        return s.hasProfile() && s.getFaceResult() != null && s.getBodyResult() != null;
    }

    /** 현재 세션 값으로 ⑤ ResultData를 산출합니다. 호출 전 {@link #requireResultReady}로 선행 조건을 확인해야 합니다. */
    public ResultData buildResult(GuestSession s) {
        StyleRecommendation rec = recommendationService.recommend(new RecommendationInput(
                s.getFaceResult(), s.getBodyResult(), s.getAge(), s.getHeightCm(), s.getWeightKg(),
                s.getHairThickness(), s.getHairCurl(), s.getHairDensity()));
        ResultBasis basis = new ResultBasis(catalog.faceLabel(s.getFaceResult()), catalog.bodyLabel(s.getBodyResult()),
                s.getFaceSource(), s.getBodySource());
        return new ResultData(s.getSessionId(), rec.ruleVersion(), KstTime.toOffset(now()), rec.type(), basis,
                rec.hairStyles(), rec.fashionStyles(), DISCLAIMER);
    }

    private void touch(GuestSession session) {
        touch(session, now());
    }

    /** 쓰기 요청 성공 → 세션 TTL 연장. */
    private void touch(GuestSession session, Instant now) {
        session.renewExpiry(now.plus(ttl));
    }

    /** 다른 모듈(챗봇 ⑩)의 쓰기 요청 성공 시 TTL 연장. 관리 상태(트랜잭션 안) 엔티티에 호출해야 반영됩니다. */
    public void extendExpiry(GuestSession session, Instant now) {
        touch(session, now);
    }

    private Instant now() {
        return KstTime.now(clock);
    }
}
