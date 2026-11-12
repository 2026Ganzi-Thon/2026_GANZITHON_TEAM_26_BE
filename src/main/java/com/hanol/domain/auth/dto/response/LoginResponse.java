package com.hanol.domain.auth.dto.response;

import com.hanol.domain.auth.service.TokenPair;

public record LoginResponse(
    String accessToken, String refreshToken, boolean isOnboardingCompleted) {

  public static LoginResponse from(TokenPair tokens, boolean onboardingCompleted) {
    return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), onboardingCompleted);
  }
}
