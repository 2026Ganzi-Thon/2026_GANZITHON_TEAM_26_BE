package com.hanol.domain.blanket.dto.response;

import com.hanol.domain.mood.entity.Emotion;

public record BlanketSlotSummaryResponse(int slotIndex, boolean filled, Emotion emotion) {}
