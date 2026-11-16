package com.hanol.domain.cheer.dto.request;

import com.hanol.domain.mood.entity.Emotion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheerSendRequest(
    @NotNull(message = "보낼 마음을 골라 주세요.") Emotion targetEmotion,
    @NotBlank(message = "패치 내용을 입력해 주세요.") @Size(max = 50, message = "패치는 50자 이내로 입력해 주세요.")
        String content) {}
