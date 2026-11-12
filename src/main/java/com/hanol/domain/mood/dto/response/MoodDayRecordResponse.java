package com.hanol.domain.mood.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record MoodDayRecordResponse(
    Long recordId, LocalDateTime recordedAt, List<String> emotions, boolean hasStory) {}
