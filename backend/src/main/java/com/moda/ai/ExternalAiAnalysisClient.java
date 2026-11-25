package com.moda.ai;

import com.moda.ai.model.BodyAnalysisResult;
import com.moda.ai.model.FaceAnalysisResult;
import com.moda.global.config.ModaProperties;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code moda.ai.mode=external} 분석 클라이언트 — AI 서버(FastAPI) 호출 (docs/api-spec.md 부록 A.1).
 * <ul>
 *   <li>{@code POST {base-url}/v1/analyze/face}: {@code image}, {@code request_id}</li>
 *   <li>{@code POST {base-url}/v1/analyze/body}: {@code image}, {@code height_cm}, {@code request_id}</li>
 *   <li>연결 타임아웃 3초, 읽기 타임아웃 얼굴 15초 / 전신 20초</li>
 * </ul>
 * 사진은 메모리 바이트 그대로 multipart로 전달하며 디스크에 쓰지 않습니다 (R-01).
 * 오류 변환 (A.6): 연결 실패·5xx → 503, 읽기 타임아웃 → 504, 응답 형식 오류·4xx → 502.
 */
public class ExternalAiAnalysisClient implements AnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalAiAnalysisClient.class);

    static final String FACE_PATH = "/v1/analyze/face";
    static final String BODY_PATH = "/v1/analyze/body";

    private final RestClient faceClient;
    private final RestClient bodyClient;
    private final AiResponseParser parser;

    public ExternalAiAnalysisClient(ModaProperties.Ai props, AiResponseParser parser) {
        this.parser = parser;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.faceClient = restClient(httpClient, props.baseUrl(), props.faceReadTimeout());
        this.bodyClient = restClient(httpClient, props.baseUrl(), props.bodyReadTimeout());
    }

    private static RestClient restClient(HttpClient httpClient, String baseUrl, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public FaceAnalysisResult analyzeFace(AnalysisImage image, AnalysisOptions options) {
        MultiValueMap<String, Object> body = imageBody(image);
        return parser.parseFace(parser.readTree(post(faceClient, FACE_PATH, body), "face"));
    }

    @Override
    public BodyAnalysisResult analyzeBody(AnalysisImage image, int heightCm, AnalysisOptions options) {
        MultiValueMap<String, Object> body = imageBody(image);
        body.add("height_cm", String.valueOf(heightCm));
        return parser.parseBody(parser.readTree(post(bodyClient, BODY_PATH, body), "body"));
    }

    /** multipart 본문: {@code image}(메모리 바이트) + {@code request_id}. */
    private static MultiValueMap<String, Object> imageBody(AnalysisImage image) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(image.contentType()));
        partHeaders.setContentDispositionFormData("image", image.filename());
        ByteArrayResource resource = new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return image.filename();
            }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("image", new HttpEntity<>(resource, partHeaders));
        if (image.requestId() != null) {
            parts.add("request_id", image.requestId());
        }
        return parts;
    }

    private String post(RestClient client, String path, MultiValueMap<String, Object> body) {
        try {
            return client.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        throw new BusinessException(ErrorCode.AI_SERVER_UNAVAILABLE, null,
                                new IllegalStateException("AI " + path + " → HTTP " + res.getStatusCode().value()));
                    })
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        throw new BusinessException(ErrorCode.AI_BAD_RESPONSE, null,
                                new IllegalStateException("AI " + path + " → HTTP " + res.getStatusCode().value()));
                    })
                    .body(String.class);
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (isReadTimeout(e)) {
                throw new BusinessException(ErrorCode.AI_TIMEOUT, null, e);
            }
            throw new BusinessException(ErrorCode.AI_SERVER_UNAVAILABLE, null, e);
        } catch (RestClientException e) {
            log.warn("[AI] {} 응답 처리 실패", path, e);
            throw new BusinessException(ErrorCode.AI_BAD_RESPONSE, null, e);
        }
    }

    /** 연결 타임아웃·연결 거부는 503, 연결 후 응답 대기 타임아웃만 504. */
    static boolean isReadTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
