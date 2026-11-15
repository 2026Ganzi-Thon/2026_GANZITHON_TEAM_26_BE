package com.hanol.domain.mood.dto.request;

import com.hanol.domain.mood.entity.Emotion;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MoodRecordCreateRequest(
    @NotEmpty(message = "감정을 선택해 주세요.") @Size(max = 2, message = "감정은 2개까지 선택할 수 있어요.")
        List<@NotNull Emotion> emotions,
    @Size(max = 300, message = "사연은 300자 이내로 입력해 주세요.") String story) {}
