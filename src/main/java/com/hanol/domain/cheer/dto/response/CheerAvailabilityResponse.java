package com.hanol.domain.cheer.dto.response;

import java.util.List;

public record CheerAvailabilityResponse(boolean sendable, List<EmotionQuestionGroup> questions) {}
