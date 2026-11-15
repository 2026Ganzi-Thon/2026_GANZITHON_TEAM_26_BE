package com.hanol.domain.mood.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record MoodRecordCreateResponse(
    Long recordId, List<String> emotions, LocalDateTime recordedAt, int todayCount) {}
