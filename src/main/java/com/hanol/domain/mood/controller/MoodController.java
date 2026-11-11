package com.hanol.domain.mood.controller;

import com.hanol.domain.mood.dto.request.StoryCheckRequest;
import com.hanol.domain.mood.dto.response.StoryCheckResponse;
import com.hanol.domain.mood.service.MoodService;
import com.hanol.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moods")
@RequiredArgsConstructor
public class MoodController {

  private final MoodService moodService;

  /** CRD-WRITE-002 사연 위기 검사. */
  @PostMapping("/stories")
  public ApiResponse<StoryCheckResponse> checkStory(@Valid @RequestBody StoryCheckRequest request) {
    return ApiResponse.ok(moodService.checkStory(request));
  }
}
