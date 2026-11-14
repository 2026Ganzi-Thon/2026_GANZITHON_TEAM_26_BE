package com.hanol.domain.cheer.service;

import com.hanol.domain.cheer.config.CheerProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 발신자 해시: HmacSHA256(userId) 를 소문자 hex 64자로. 같은 userId 는 항상 같은 값이다. */
@Component
public class CheerSenderHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  public CheerSenderHasher(CheerProperties properties) {
    this.key =
        new SecretKeySpec(
            properties.senderHmacSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  public String hash(Long userId) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      byte[] digest = mac.doFinal(String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("발신자 해시를 만들 수 없습니다.", e);
    }
  }
}
