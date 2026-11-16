package com.hanol.domain.interaction.dto.response;

import java.util.List;

public record InteractionMapResponse(
    InteractionMapStatus status,
    List<EmotionCountResponse> emotions,
    boolean blanketNotStackedNotice) {}
