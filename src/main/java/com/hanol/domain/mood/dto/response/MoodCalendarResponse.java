package com.hanol.domain.mood.dto.response;

import java.time.LocalDate;
import java.util.List;

public record MoodCalendarResponse(List<LocalDate> recordedDates) {}
