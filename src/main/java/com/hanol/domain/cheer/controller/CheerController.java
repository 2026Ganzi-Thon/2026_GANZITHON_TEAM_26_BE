package com.hanol.domain.cheer.controller;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.CheerReceiveResponse;
import com.hanol.domain.cheer.dto.response.CheerReportResponse;
import com.hanol.domain.cheer.dto.response.CheerSendResponse;
import com.hanol.domain.cheer.service.CheerReceiveService;
import com.hanol.domain.cheer.service.CheerReportService;
import com.hanol.domain.cheer.service.CheerService;
import com.hanol.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cheers")
@RequiredArgsConstructor
public class CheerController {

  private final CheerService cheerService;
  private final CheerReceiveService cheerReceiveService;
  private final CheerReportService cheerReportService;

  /** BLK-SEND-001 보내기 가능 확인. */
  @GetMapping("/availability")
  public ApiResponse<CheerAvailabilityResponse> getAvailability(
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(cheerService.getAvailability(userId));
  }

  /** BLK-SEND-002 패치 검사·발송. 위기여도 200 이다. */
  @PostMapping("")
  public ApiResponse<CheerSendResponse> send(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CheerSendRequest request) {
    return ApiResponse.ok(cheerService.send(userId, request));
  }

  /** BLK-RCV-001·002 패치 받기. 오늘 가장 마지막 기록의 도안별로 받는다. */
  @PostMapping("/receive")
  public ApiResponse<CheerReceiveResponse> receive(@AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(cheerReceiveService.receive(userId));
  }

  /** BLK-RPT-001 패치 신고. 숨기고 같은 담요 칸을 새 패치로 바꾼다. */
  @PostMapping("/{cheerId}/report")
  public ApiResponse<CheerReportResponse> report(
      @AuthenticationPrincipal Long userId, @PathVariable Long cheerId) {
    return ApiResponse.ok(cheerReportService.report(userId, cheerId));
  }
}
