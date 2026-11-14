package com.hanol.domain.cheer.controller;

import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.service.CheerService;
import com.hanol.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
