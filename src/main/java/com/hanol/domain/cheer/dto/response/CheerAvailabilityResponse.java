package com.hanol.domain.cheer.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record CheerAvailabilityResponse(
    boolean sendable, LocalDateTime restrictedUntil, List<EmotionQuestionGroup> questions) {}
