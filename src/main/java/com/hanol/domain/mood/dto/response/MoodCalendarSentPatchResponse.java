package com.hanol.domain.mood.dto.response;

import java.time.LocalDate;

public record MoodCalendarSentPatchResponse(LocalDate date, int count) {}
