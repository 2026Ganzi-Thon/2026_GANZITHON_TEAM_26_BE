package com.hanol.domain.cheer.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 전화번호·이메일·URL 검사. */
@Component
public class CheerContactMatcher {

  private static final List<Pattern> PATTERNS =
      List.of(
          Pattern.compile("(?<!\\d)0\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)"),
          Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}"),
          Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE),
          Pattern.compile("(?<![A-Za-z0-9])www\\.\\S+", Pattern.CASE_INSENSITIVE),
          Pattern.compile(
              "(?<![A-Za-z0-9.@-])[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*"
                  + "\\.(com|net|org|kr|io|co|me|ly|gg|xyz|app|dev|info|biz|site|shop|link|to|tv)"
                  + "(?![A-Za-z0-9])",
              Pattern.CASE_INSENSITIVE));

  public boolean matches(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
  }
}
