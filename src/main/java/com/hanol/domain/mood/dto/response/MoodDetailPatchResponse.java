package com.hanol.domain.mood.dto.response;

import com.hanol.domain.mood.entity.ReceiveStatus;

public record MoodDetailPatchResponse(String emotion, ReceiveStatus status, String message) {}
