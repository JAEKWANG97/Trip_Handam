package com.ssafy.handam.feed.presentation.request.comment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        @NotNull Long userId,
        @NotEmpty String content
) {
}
