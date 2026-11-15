package com.hanol.domain.mood.dto.response;

import java.util.List;

public record MoodDetailResponse(
    List<String> emotions, List<MoodDetailPatchResponse> patches, String story) {}
