package com.hanol.domain.interaction.controller;

import com.hanol.domain.interaction.dto.response.InteractionMapResponse;
import com.hanol.domain.interaction.dto.response.RestRoomEntryResponse;
import com.hanol.domain.interaction.dto.response.RestRoomSignalResponse;
import com.hanol.domain.interaction.service.InteractionMapService;
import com.hanol.domain.interaction.service.RestRoomService;
import com.hanol.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions")
@RequiredArgsConstructor
public class InteractionController {

  private final InteractionMapService interactionMapService;
  private final RestRoomService restRoomService;

  /** ITR-MAP-001 온기 지도 인원 표시. */
  @GetMapping("/map")
  public ApiResponse<InteractionMapResponse> getMap(@AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(interactionMapService.getMap(userId));
  }

  /** ITR-REST-001 함께 쉬는 방 입장. */
  @PostMapping("/rest-room")
  public ApiResponse<RestRoomEntryResponse> enterRestRoom(@AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(restRoomService.enter(userId));
  }

  /** ITR-REST-001 함께 쉬는 방 신호(하트비트). */
  @PutMapping("/rest-room")
  public ApiResponse<RestRoomSignalResponse> signalRestRoom(@AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(restRoomService.signal(userId));
  }

  /** ITR-REST-001 함께 쉬는 방 퇴장. */
  @DeleteMapping("/rest-room")
  public ApiResponse<Void> exitRestRoom(@AuthenticationPrincipal Long userId) {
    restRoomService.exit(userId);
    return ApiResponse.ok();
  }
}
