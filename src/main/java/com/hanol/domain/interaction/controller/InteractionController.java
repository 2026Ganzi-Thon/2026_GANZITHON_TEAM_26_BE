package com.hanol.domain.interaction.controller;

import com.hanol.domain.interaction.dto.response.InteractionMapResponse;
import com.hanol.domain.interaction.service.InteractionMapService;
import com.hanol.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions")
@RequiredArgsConstructor
public class InteractionController {

  private final InteractionMapService interactionMapService;

  /** ITR-MAP-001 온기 지도 인원 표시. */
  @GetMapping("/map")
  public ApiResponse<InteractionMapResponse> getMap(@AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(interactionMapService.getMap(userId));
  }
}
