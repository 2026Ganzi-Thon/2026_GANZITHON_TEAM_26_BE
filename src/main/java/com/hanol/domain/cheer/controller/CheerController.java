package com.hanol.domain.cheer.controller;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.CheerSendResponse;
import com.hanol.domain.cheer.service.CheerService;
import com.hanol.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cheers")
@RequiredArgsConstructor
public class CheerController {

  private final CheerService cheerService;

  /** BLK-SEND-001 보내기 가능 확인. */
  @GetMapping("/availability")
  public ApiResponse<CheerAvailabilityResponse> getAvailability() {
    return ApiResponse.ok(cheerService.getAvailability());
  }

  /** BLK-SEND-002 패치 검사·발송. 위기여도 200 이다. */
  @PostMapping("")
  public ApiResponse<CheerSendResponse> send(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CheerSendRequest request) {
    return ApiResponse.ok(cheerService.send(userId, request));
  }
}
