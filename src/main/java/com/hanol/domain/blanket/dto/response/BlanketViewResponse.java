package com.hanol.domain.blanket.dto.response;

import java.util.List;

public record BlanketViewResponse(
    Long blanketId, List<BlanketSlotSummaryResponse> slots, boolean hasPrevious, boolean hasNext) {}
