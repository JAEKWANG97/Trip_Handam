package com.ssafy.handam.accompanyboard;

import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;

import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;

public interface ApiDocumentUtils {
    static OperationRequestPreprocessor getDocumentRequest() {
        return preprocessRequest(
//                        modifyUris() // (1)
//                                .scheme("https")
//                                .host("docs.api.com")
//                                .removePort(),
                        prettyPrint()); // (2)
    }

    static OperationResponsePreprocessor getDocumentResponse() {
        return preprocessResponse(prettyPrint()); // (3)
    }
}
