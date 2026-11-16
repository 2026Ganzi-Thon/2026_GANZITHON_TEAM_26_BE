package com.hanol.domain.cheer.service;

/** 패치 검수. 트랜잭션 밖에서 호출한다. */
public interface CheerReviewer {

  CheerReviewResult review(String content);
}
