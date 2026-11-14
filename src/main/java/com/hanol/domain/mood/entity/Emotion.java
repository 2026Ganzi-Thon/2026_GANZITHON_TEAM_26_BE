package com.hanol.domain.mood.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 마음 고르기 감정 카드. label 은 화면에 보여주는 문구다. */
@Getter
@RequiredArgsConstructor
public enum Emotion {
  UNMOTIVATED("아무것도 하기 싫어요"),
  HOPELESS_FUTURE("미래가 막막해요"),
  ANXIETY("불안해서 잠이 안 와요"),
  LONELY("혼자인 것 같아요"),
  EXHAUSTED("지쳐서 아무 생각이 없어요"),
  INADEQUATE("내가 부족한 것 같아요"),
  NOWHERE_TO_VENT("화를 풀 곳이 없어요"),
  TEARFUL("괜히 눈물이 나요"),
  RESTLESS("쉬고 싶은데 못 쉬어요"),
  OVERWHELMED("사람들이 버거워요"),
  UNSURE("잘 모르겠어요");

  private final String label;
}
