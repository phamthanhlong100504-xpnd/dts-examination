package dts.com.examination.application.client;

import dts.com.examination.api.response.InternalQuestionDetailResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ContentBuilderClientFallback implements ContentBuilderClient {

    @Override
    public List<Map<String, Object>> getQuestionsMetadata(UUID contentId, String contentType) {
        throw new dts.com.examination.application.exception.BusinessRuleException(
                "Content service unavailable: unable to fetch question metadata. Please try again later."
        );
    }

    @Override
    public List<InternalQuestionDetailResponse> getQuestionsBatch(List<UUID> questionIds) {
        throw new dts.com.examination.application.exception.BusinessRuleException(
                "Content service unavailable: unable to fetch question details. Please try again later."
        );
    }
}