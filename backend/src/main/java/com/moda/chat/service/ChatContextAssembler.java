package com.moda.chat.service;

import com.moda.chat.model.ChatContext;
import com.moda.session.domain.GuestSession;
import com.moda.session.dto.AnalysisDetailResponses.BodyDetail;
import com.moda.session.dto.AnalysisDetailResponses.FaceDetail;
import com.moda.session.dto.SessionResponses.ResultData;
import com.moda.session.service.GuestSessionService;
import com.moda.session.service.SessionViewMapper;
import org.springframework.stereotype.Component;

/**
 * DB의 최신 세션 값으로 {@link ChatContext}를 만듭니다: {@code profileInput}(몸무게 제외) + {@code faceDetail}({@code aiReason} 포함)
 * + {@code bodyDetail} + {@code resultData}. ⑦ {@code PATCH /profile}로 보정한 값이 다음 턴에 즉시 반영됩니다.
 * 호출 전 {@link GuestSessionService#requireResultReady}로 선행 조건을 확인해야 합니다.
 */
@Component
public class ChatContextAssembler {

    private final GuestSessionService sessionService;
    private final SessionViewMapper viewMapper;

    public ChatContextAssembler(GuestSessionService sessionService, SessionViewMapper viewMapper) {
        this.sessionService = sessionService;
        this.viewMapper = viewMapper;
    }

    public ChatContext build(GuestSession session) {
        ResultData result = sessionService.buildResult(session);
        FaceDetail face = viewMapper.faceDetail(session);
        BodyDetail body = viewMapper.bodyDetail(session);
        return new ChatContext(
                result.type().typeCode(),
                result.type().typeName(),
                result.type().description(),
                result.type().stylePoints(),
                face.result(),
                face.status(),
                face.source(),
                face.description(),
                face.aiReason(),
                body.result(),
                body.status(),
                body.source(),
                body.description(),
                session.getAge(),
                session.getHeightCm(),
                session.getHairThickness(),
                session.getHairCurl(),
                session.getHairDensity(),
                result.hairStyles(),
                result.fashionStyles());
    }
}
