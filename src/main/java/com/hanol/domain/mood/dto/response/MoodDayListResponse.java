package com.hanol.domain.mood.dto.response;

import java.util.List;

public record MoodDayListResponse(Integer sentPatchCount, List<MoodDayRecordResponse> records) {}
