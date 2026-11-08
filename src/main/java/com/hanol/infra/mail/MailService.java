package com.hanol.infra.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 메일 발송 (SMTP). 발신 계정은 MAIL_USERNAME / MAIL_PASSWORD 환경변수로 설정한다. */
@Component
@RequiredArgsConstructor
public class MailService {

  private static final String VERIFICATION_SUBJECT = "[한 올] 이메일 인증번호 안내";
  private static final String VERIFICATION_TEXT =
      """
      안녕하세요, 한 올입니다.

      아래 인증번호 6자리를 앱에 입력해 주세요.

      인증번호: %s

      인증번호는 5분 동안 유효합니다.
      본인이 요청하지 않았다면 이 메일은 무시해 주세요.
      """;

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String from;

  /**
   * 인증번호 메일을 보낸다.
   *
   * @throws MailException SMTP 연결·인증·발송에 실패한 경우
   */
  public void sendVerificationCode(String to, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(VERIFICATION_SUBJECT);
    message.setText(VERIFICATION_TEXT.formatted(code));
    mailSender.send(message);
  }
}
