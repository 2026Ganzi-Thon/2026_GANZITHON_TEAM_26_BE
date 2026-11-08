package com.hanol.domain.auth.dto.response;

public record TokenReissueResponse(String accessToken) {

  public static TokenReissueResponse from(String accessToken) {
    return new TokenReissueResponse(accessToken);
  }
}
