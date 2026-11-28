package com.moda.session.domain;

import com.moda.ai.model.AnalysisValues.CandidateValue;
import com.moda.ai.model.AnalysisValues.FeatureValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaValue;
import com.moda.ai.model.AnalysisValues.WarningValue;
import com.moda.common.enums.AnalysisSource;
import com.moda.common.enums.AnalysisStatus;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 비로그인 게스트 체험 세션 (docs/api-spec.md 1.4).
 * <ul>
 *   <li>TTL 30분, 쓰기 요청(POST/PATCH) 성공 시마다 연장. 만료 후 접근은 {@code 410 SESSION_EXPIRED}</li>
 *   <li>사진·사진 경로·랜드마크 좌표는 저장하지 않음 (R-01)</li>
 *   <li>{@code userId}는 2차 목표(회원 귀속)용 예약 컬럼 — MVP 응답에 노출하지 않음</li>
 * </ul>
 * 얼굴/체형 분석 상태: {@code source}(현재 적용 값의 출처), {@code result}(추천 계산에 쓰는 값), {@code aiResult}(AI 원본 판정, 수동 보정해도 보존).
 */
@Entity
@Table(name = "guest_session", indexes = @Index(name = "idx_guest_session_expires_at", columnList = "expires_at"))
public class GuestSession {

    static final int JSON_COLUMN_LENGTH = 20000;

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    /** 2차 목표: 로그인 시 세션을 사용자에게 귀속. MVP에서는 항상 null. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "photo_consent", nullable = false)
    private boolean photoConsent;

    @Column(name = "device_label", length = 50)
    private String deviceLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // ---------------------------------------------------------------- 기본 정보 (②)

    @Column(name = "age")
    private Integer age;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "weight_kg")
    private Integer weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "hair_thickness", length = 10)
    private HairThickness hairThickness;

    @Enumerated(EnumType.STRING)
    @Column(name = "hair_curl", length = 12)
    private HairCurl hairCurl;

    @Enumerated(EnumType.STRING)
    @Column(name = "hair_density", length = 10)
    private HairDensity hairDensity;

    // ---------------------------------------------------------------- 얼굴 분석 (③)

    @Enumerated(EnumType.STRING)
    @Column(name = "face_source", length = 10)
    private AnalysisSource faceSource;

    @Column(name = "face_result", length = 30)
    private FaceShape faceResult;

    @Column(name = "face_ai_result", length = 30)
    private FaceShape faceAiResult;

    @Column(name = "face_confidence")
    private Double faceConfidence;

    @Column(name = "face_borderline")
    private Boolean faceBorderline;

    /** AI v8 평균 대비 특징 요약 문장 (aiResult 기준). */
    @Column(name = "face_ai_reason", length = 1000)
    private String faceAiReason;

    @Convert(converter = JsonColumnConverters.CandidateList.class)
    @Column(name = "face_candidates", length = JSON_COLUMN_LENGTH)
    private List<CandidateValue> faceCandidates = new ArrayList<>();

    @Convert(converter = JsonColumnConverters.WarningList.class)
    @Column(name = "face_warnings", length = JSON_COLUMN_LENGTH)
    private List<WarningValue> faceWarnings = new ArrayList<>();

    @Convert(converter = JsonColumnConverters.FeatureList.class)
    @Column(name = "face_features", length = JSON_COLUMN_LENGTH)
    private List<FeatureValue> faceFeatures = new ArrayList<>();

    @Column(name = "face_analyzed_at")
    private Instant faceAnalyzedAt;

    // ---------------------------------------------------------------- 체형 분석 (④)

    @Enumerated(EnumType.STRING)
    @Column(name = "body_source", length = 10)
    private AnalysisSource bodySource;

    @Column(name = "body_result", length = 30)
    private BodyType bodyResult;

    @Column(name = "body_ai_result", length = 30)
    private BodyType bodyAiResult;

    @Column(name = "body_confidence")
    private Double bodyConfidence;

    @Column(name = "body_borderline")
    private Boolean bodyBorderline;

    @Convert(converter = JsonColumnConverters.CandidateList.class)
    @Column(name = "body_candidates", length = JSON_COLUMN_LENGTH)
    private List<CandidateValue> bodyCandidates = new ArrayList<>();

    @Convert(converter = JsonColumnConverters.WarningList.class)
    @Column(name = "body_warnings", length = JSON_COLUMN_LENGTH)
    private List<WarningValue> bodyWarnings = new ArrayList<>();

    @Convert(converter = JsonColumnConverters.FeatureList.class)
    @Column(name = "body_features", length = JSON_COLUMN_LENGTH)
    private List<FeatureValue> bodyFeatures = new ArrayList<>();

    @Convert(converter = JsonColumnConverters.SizeKorea.class)
    @Column(name = "body_compare_size_korea", length = JSON_COLUMN_LENGTH)
    private SizeKoreaValue bodyCompareToSizeKorea;

    @Column(name = "body_height_cm_used")
    private Integer bodyHeightCmUsed;

    @Column(name = "body_analyzed_at")
    private Instant bodyAnalyzedAt;

    protected GuestSession() {
    }

    public static GuestSession create(String sessionId, boolean photoConsent, String deviceLabel, Instant now, Instant expiresAt) {
        GuestSession s = new GuestSession();
        s.sessionId = sessionId;
        s.photoConsent = photoConsent;
        s.deviceLabel = deviceLabel;
        s.createdAt = now;
        s.expiresAt = expiresAt;
        return s;
    }

    // ---------------------------------------------------------------- 수명

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void renewExpiry(Instant newExpiresAt) {
        this.expiresAt = newExpiresAt;
    }

    // ---------------------------------------------------------------- 기본 정보

    public boolean hasProfile() {
        return age != null && heightCm != null && hairThickness != null && hairCurl != null && hairDensity != null;
    }

    /**
     * 기본 정보 전체 덮어쓰기. 이미 AI 체형 분석이 끝난 뒤 키가 바뀌면 키 기준이 달라지므로 AI 체형 결과를 초기화합니다.
     *
     * @return AI 체형 결과가 초기화되었으면 {@code true} ({@code bodyAnalysisReset})
     */
    public boolean updateProfile(int age, int heightCm, Integer weightKg, HairThickness thickness, HairCurl curl, HairDensity density) {
        boolean heightChanged = this.heightCm != null && this.heightCm != heightCm;
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.hairThickness = thickness;
        this.hairCurl = curl;
        this.hairDensity = density;
        return heightChanged && resetBodyAiResult();
    }

    /** 키 변경으로 AI 체형 결과를 초기화. AI 값을 쓰던 중이면 체형 미확정, 사용자가 직접 고른 값(MANUAL)은 유지. */
    private boolean resetBodyAiResult() {
        if (bodyAiResult == null) {
            return false;
        }
        if (bodySource == AnalysisSource.AI) {
            bodySource = null;
            bodyResult = null;
        }
        bodyAiResult = null;
        bodyConfidence = null;
        bodyBorderline = null;
        bodyCandidates = new ArrayList<>();
        bodyWarnings = new ArrayList<>();
        bodyFeatures = new ArrayList<>();
        bodyCompareToSizeKorea = null;
        bodyHeightCmUsed = null;
        bodyAnalyzedAt = null;
        return true;
    }

    // ---------------------------------------------------------------- 분석 결과 반영 (usable=true 일 때만 호출)

    public void applyFaceAnalysis(FaceShape shape, Double confidence, Boolean borderline, String aiReason,
                                  List<CandidateValue> candidates, List<WarningValue> warnings, List<FeatureValue> features,
                                  Instant analyzedAt) {
        this.faceSource = AnalysisSource.AI;
        this.faceResult = Objects.requireNonNull(shape);
        this.faceAiResult = shape;
        this.faceConfidence = confidence;
        this.faceBorderline = borderline;
        this.faceAiReason = aiReason;
        this.faceCandidates = new ArrayList<>(candidates);
        this.faceWarnings = new ArrayList<>(warnings);
        this.faceFeatures = new ArrayList<>(features);
        this.faceAnalyzedAt = analyzedAt;
    }

    public void applyBodyAnalysis(BodyType type, Double confidence, Boolean borderline, List<CandidateValue> candidates,
                                  List<WarningValue> warnings, List<FeatureValue> features, SizeKoreaValue compare,
                                  int heightCmUsed, Instant analyzedAt) {
        this.bodySource = AnalysisSource.AI;
        this.bodyResult = Objects.requireNonNull(type);
        this.bodyAiResult = type;
        this.bodyConfidence = confidence;
        this.bodyBorderline = borderline;
        this.bodyCandidates = new ArrayList<>(candidates);
        this.bodyWarnings = new ArrayList<>(warnings);
        this.bodyFeatures = new ArrayList<>(features);
        this.bodyCompareToSizeKorea = compare;
        this.bodyHeightCmUsed = heightCmUsed;
        this.bodyAnalyzedAt = analyzedAt;
    }

    // ---------------------------------------------------------------- 수동 보정 (⑦)

    /** @return 값이 실제로 바뀌었으면 {@code true}. 같은 값이면 출처(source)도 그대로 둡니다. */
    public boolean correctFace(FaceShape shape) {
        if (shape == faceResult) {
            return false;
        }
        this.faceResult = shape;
        this.faceSource = AnalysisSource.MANUAL;
        return true;
    }

    public boolean correctBody(BodyType type) {
        if (type == bodyResult) {
            return false;
        }
        this.bodyResult = type;
        this.bodySource = AnalysisSource.MANUAL;
        return true;
    }

    public boolean changeHairThickness(HairThickness value) {
        boolean changed = value != hairThickness;
        this.hairThickness = value;
        return changed;
    }

    public boolean changeHairCurl(HairCurl value) {
        boolean changed = value != hairCurl;
        this.hairCurl = value;
        return changed;
    }

    public boolean changeHairDensity(HairDensity value) {
        boolean changed = value != hairDensity;
        this.hairDensity = value;
        return changed;
    }

    // ---------------------------------------------------------------- 상태

    public AnalysisStatus faceStatus() {
        return status(faceSource, faceAiResult != null);
    }

    public AnalysisStatus bodyStatus() {
        return status(bodySource, bodyAiResult != null);
    }

    /** api-spec.md ⑥ status 판정 규칙. */
    static AnalysisStatus status(AnalysisSource source, boolean hasAiResult) {
        if (source == null) {
            return AnalysisStatus.NOT_ANALYZED;
        }
        if (source == AnalysisSource.AI) {
            return AnalysisStatus.ANALYZED;
        }
        return hasAiResult ? AnalysisStatus.CORRECTED : AnalysisStatus.MANUAL;
    }

    // ---------------------------------------------------------------- getters

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isPhotoConsent() {
        return photoConsent;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getAge() {
        return age;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public Integer getWeightKg() {
        return weightKg;
    }

    public HairThickness getHairThickness() {
        return hairThickness;
    }

    public HairCurl getHairCurl() {
        return hairCurl;
    }

    public HairDensity getHairDensity() {
        return hairDensity;
    }

    public AnalysisSource getFaceSource() {
        return faceSource;
    }

    public FaceShape getFaceResult() {
        return faceResult;
    }

    public FaceShape getFaceAiResult() {
        return faceAiResult;
    }

    public Double getFaceConfidence() {
        return faceConfidence;
    }

    public Boolean getFaceBorderline() {
        return faceBorderline;
    }

    public String getFaceAiReason() {
        return faceAiReason;
    }

    public List<CandidateValue> getFaceCandidates() {
        return faceCandidates == null ? List.of() : faceCandidates;
    }

    public List<WarningValue> getFaceWarnings() {
        return faceWarnings == null ? List.of() : faceWarnings;
    }

    public List<FeatureValue> getFaceFeatures() {
        return faceFeatures == null ? List.of() : faceFeatures;
    }

    public Instant getFaceAnalyzedAt() {
        return faceAnalyzedAt;
    }

    public AnalysisSource getBodySource() {
        return bodySource;
    }

    public BodyType getBodyResult() {
        return bodyResult;
    }

    public BodyType getBodyAiResult() {
        return bodyAiResult;
    }

    public Double getBodyConfidence() {
        return bodyConfidence;
    }

    public Boolean getBodyBorderline() {
        return bodyBorderline;
    }

    public List<CandidateValue> getBodyCandidates() {
        return bodyCandidates == null ? List.of() : bodyCandidates;
    }

    public List<WarningValue> getBodyWarnings() {
        return bodyWarnings == null ? List.of() : bodyWarnings;
    }

    public List<FeatureValue> getBodyFeatures() {
        return bodyFeatures == null ? List.of() : bodyFeatures;
    }

    public SizeKoreaValue getBodyCompareToSizeKorea() {
        return bodyCompareToSizeKorea;
    }

    public Integer getBodyHeightCmUsed() {
        return bodyHeightCmUsed;
    }

    public Instant getBodyAnalyzedAt() {
        return bodyAnalyzedAt;
    }
}
