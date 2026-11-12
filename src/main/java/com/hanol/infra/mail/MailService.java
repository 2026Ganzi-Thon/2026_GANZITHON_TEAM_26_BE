package com.hanol.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 메일 발송 (SMTP). 발신 계정은 MAIL_USERNAME / MAIL_PASSWORD 환경변수로 설정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailService {

  private static final String VERIFICATION_SUBJECT = "[PATCH] 이메일 인증번호 안내";
  private static final String VERIFICATION_TEXT =
      """
      안녕하세요, PATCH입니다.

      아래 인증번호 6자리를 앱에 입력해 주세요.

      인증번호: %s

      인증번호는 5분 동안 유효합니다.
      본인이 요청하지 않았다면 이 메일은 무시해 주세요.
      """;

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String from;

  /** 인증번호 메일을 비동기로 보낸다. 실패하면 로그만 남긴다. */
  @Async
  public void sendVerificationCode(String to, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(VERIFICATION_SUBJECT);
    message.setText(VERIFICATION_TEXT.formatted(code));
    try {
      mailSender.send(message);
      log.info("인증 메일 발송 완료");
    } catch (MailException exception) {
      log.error("인증 메일 발송 실패", exception);
    }
  }
}
