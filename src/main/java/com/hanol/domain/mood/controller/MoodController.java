package com.hanol.domain.mood.controller;

import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.dto.request.StoryCheckRequest;
import com.hanol.domain.mood.dto.response.MoodRecordCreateResponse;
import com.hanol.domain.mood.dto.response.StoryCheckResponse;
import com.hanol.domain.mood.service.MoodService;
import com.hanol.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moods")
@RequiredArgsConstructor
public class MoodController {

  private final MoodService moodService;

  /** CRD-WRITE-003 마음 기록 저장. */
  @PostMapping("")
  public ResponseEntity<ApiResponse<MoodRecordCreateResponse>> createRecord(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody MoodRecordCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(moodService.createRecord(userId, request)));
  }

  /** CRD-WRITE-002 사연 위기 검사. */
  @PostMapping("/stories")
  public ApiResponse<StoryCheckResponse> checkStory(@Valid @RequestBody StoryCheckRequest request) {
    return ApiResponse.ok(moodService.checkStory(request));
  }
}
