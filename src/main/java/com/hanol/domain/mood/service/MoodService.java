package com.hanol.domain.mood.service;

import com.hanol.domain.alert.service.CrisisDetector;
import com.hanol.domain.mood.dto.request.StoryCheckRequest;
import com.hanol.domain.mood.dto.response.StoryCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MoodService {

  private final CrisisDetector crisisDetector;

  public StoryCheckResponse checkStory(StoryCheckRequest request) {
    return new StoryCheckResponse(crisisDetector.isCrisis(request.content()));
  }
}
