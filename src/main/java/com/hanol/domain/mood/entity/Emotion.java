package com.hanol.domain.mood.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 마음 고르기 감정 카드. label 은 화면에 보여주는 문구다. cheerQuestion 은 패치 보내기 질문이다. */
@Getter
@RequiredArgsConstructor
public enum Emotion {
  UNMOTIVATED("아무것도 하기 싫어요", "아무것도 하기 싫던 날, 그래도 했던 작은 일 하나는 뭐였어요?"),
  HOPELESS_FUTURE("미래가 막막해요", "이 마음일 때 나를 조금 버티게 한 건 뭐였어요?"),
  ANXIETY("불안해서 잠이 안 와요", "잠 못 들던 밤, 마음이 조금 가라앉았던 방법이 있었나요?"),
  LONELY("혼자인 것 같아요", "혼자인 것 같던 때, 곁에 누가 있다고 느끼게 해준 건 뭐였어요?"),
  EXHAUSTED("지쳐서 아무 생각이 없어요", "완전히 지쳤을 때, 기운이 조금 돌아왔던 순간은 언제였어요?"),
  INADEQUATE("내가 부족한 것 같아요", "내가 부족하게 느껴질 때, 스스로에게 해준 말이 있었나요?"),
  NOWHERE_TO_VENT("화를 풀 곳이 없어요", "화난 상황에서 어떻게 진정했나요?"),
  TEARFUL("괜히 눈물이 나요", "이유 없이 눈물이 나던 날, 뭐가 조금 도움이 됐어요?"),
  RESTLESS("쉬고 싶은데 못 쉬어요", "쉴 틈이 없을 때, 잠깐이라도 숨 돌린 방법이 있었나요?"),
  OVERWHELMED("사람들이 버거워요", "사람들이 버거울 때, 나를 지킨 방법이 있었나요?"),
  UNSURE("잘 모르겠어요", "마음이 뭔지 모르겠던 날, 그래도 괜찮아졌던 계기가 있었나요?");

  private final String label;
  private final String cheerQuestion;
}
