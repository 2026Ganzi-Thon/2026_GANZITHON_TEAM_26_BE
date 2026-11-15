package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanol.domain.cheer.config.CheerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheerSenderHasherTest {

  private static final String SECRET = "test-sender-hmac-secret";

  private final CheerSenderHasher hasher = new CheerSenderHasher(new CheerProperties(SECRET));

  @Test
  @DisplayName("같은 userId 는 항상 같은 해시")
  void sameUserIdGivesSameHash() {
    assertThat(hasher.hash(1L)).isEqualTo(hasher.hash(1L));
  }

  @Test
  @DisplayName("다른 userId 는 다른 해시")
  void differentUserIdGivesDifferentHash() {
    assertThat(hasher.hash(1L)).isNotEqualTo(hasher.hash(2L));
  }

  @Test
  @DisplayName("해시는 소문자 hex 64자")
  void hashIsLowercaseHex64() {
    assertThat(hasher.hash(1L)).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  @DisplayName("키가 다르면 같은 userId 라도 해시가 다르다")
  void differentKeyGivesDifferentHash() {
    CheerSenderHasher otherHasher =
        new CheerSenderHasher(new CheerProperties("another-sender-hmac-secret"));

    assertThat(otherHasher.hash(1L)).isNotEqualTo(hasher.hash(1L));
  }

  @Test
  @DisplayName("키가 비어 있으면 CheerProperties 생성 시 IllegalStateException")
  void rejectEmptySecret() {
    assertThatThrownBy(() -> new CheerProperties("")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("키가 공백이면 CheerProperties 생성 시 IllegalStateException")
  void rejectBlankSecret() {
    assertThatThrownBy(() -> new CheerProperties("   ")).isInstanceOf(IllegalStateException.class);
  }
}
