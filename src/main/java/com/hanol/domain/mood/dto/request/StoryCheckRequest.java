package com.hanol.domain.mood.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoryCheckRequest(
    @NotBlank(message = "사연을 입력해 주세요.") @Size(max = 300, message = "사연은 300자 이내로 입력해 주세요.")
        String content) {}
