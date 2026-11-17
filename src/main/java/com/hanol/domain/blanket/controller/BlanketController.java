package com.hanol.domain.blanket.controller;

import com.hanol.domain.blanket.dto.response.BlanketSlotResponse;
import com.hanol.domain.blanket.dto.response.BlanketViewResponse;
import com.hanol.domain.blanket.service.BlanketService;
import com.hanol.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blankets")
@RequiredArgsConstructor
public class BlanketController {

  private final BlanketService blanketService;

  /** BLK-VIEW-001 담요 조회. blanketId 미지정 시 현재(가장 최근) 담요. */
  @GetMapping("")
  public ApiResponse<BlanketViewResponse> getBlanket(
      @AuthenticationPrincipal Long userId, @RequestParam(required = false) Long blanketId) {
    return ApiResponse.ok(blanketService.getBlanket(userId, blanketId));
  }

  /** BLK-VIEW-002 패치 카드 조회. */
  @GetMapping("/{blanketId}/slots/{slotIndex}")
  public ApiResponse<BlanketSlotResponse> getSlot(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long blanketId,
      @PathVariable int slotIndex) {
    return ApiResponse.ok(blanketService.getSlot(userId, blanketId, slotIndex));
  }
}
