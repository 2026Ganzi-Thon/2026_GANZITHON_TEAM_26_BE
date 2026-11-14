package com.hanol.domain.alert.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 맥락과 상관없이 바로 위기로 보는 위험 단어 검사. */
@Component
public class CrisisKeywordMatcher {

  private static final List<String> KOREAN_KEYWORDS =
      List.of(
          "죽고싶", "사라지고싶", "없어지고싶", "자살하고싶", "자살할까", "자살할생각", "자살시도", "자살충동", "자해하고싶", "자해충동",
          "자해를했", "자해를하", "자해했어", "자해했었", "극단적선택", "목숨을끊", "손목을긋", "뛰어내리고싶", "목을매고", "목매달", "목을매달",
          "유서를쓰", "유서를써", "유서쓰", "유서써", "삶을끝내고싶", "인생을끝내고싶", "삶을끝낼", "인생을끝낼", "생을마감");

  private static final List<Pattern> ENGLISH_KEYWORDS =
      List.of("want to die", "kill myself", "suicide").stream()
          .map(keyword -> Pattern.compile("(?<![a-z0-9])" + keyword + "(?![a-z0-9])"))
          .toList();

  private static final Pattern NON_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");
  private static final Pattern SYMBOL = Pattern.compile("[^\\p{L}\\p{N}\\s]");
  private static final Pattern WHITESPACES = Pattern.compile("\\s+");

  public boolean matches(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    String compact = NON_LETTER_OR_DIGIT.matcher(content).replaceAll("");
    if (KOREAN_KEYWORDS.stream().anyMatch(compact::contains)) {
      return true;
    }
    String spaced = SYMBOL.matcher(content.toLowerCase(Locale.ROOT)).replaceAll(" ");
    String normalized = WHITESPACES.matcher(spaced).replaceAll(" ").trim();
    return ENGLISH_KEYWORDS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
  }
}
