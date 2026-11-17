package com.hanol.domain.interaction.dto.response;

import java.time.LocalDateTime;

public record RestRoomEntryResponse(int currentCount, LocalDateTime enteredAt) {}
