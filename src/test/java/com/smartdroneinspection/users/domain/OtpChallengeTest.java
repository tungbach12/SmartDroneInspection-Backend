package com.smartdroneinspection.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import com.smartdroneinspection.users.domain.enums.OtpChallengePurpose;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginOtpChallengeTest {

  @Test
  void verifiesCodeOnceAndRejectsReuse() {
    Instant now = Instant.parse("2026-10-30T10:00:00Z");
    OtpChallenge challenge = loginChallenge(now);
    challenge.deliver(LoginOtpChannel.EMAIL, "hashed-code", now);

    assertThat(challenge.verify("hashed-code", now.plusSeconds(1))).isTrue();
    assertThat(challenge.verify("hashed-code", now.plusSeconds(2))).isFalse();
  }

  @Test
  void expiresOtpAndLocksAfterFiveWrongCodes() {
    Instant now = Instant.parse("2026-10-30T10:00:00Z");
    OtpChallenge expiring = loginChallenge(now);
    expiring.deliver(LoginOtpChannel.SMS, "hashed-code", now);
    assertThat(expiring.verify("hashed-code", now.plusSeconds(301))).isFalse();

    OtpChallenge guessing = loginChallenge(now);
    guessing.deliver(LoginOtpChannel.SMS, "hashed-code", now);
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(guessing.verify("wrong-code", now.plusSeconds(attempt + 1))).isFalse();
    }
    assertThat(guessing.activeAt(now.plusSeconds(6))).isFalse();
  }

  @Test
  void capsOtpResendsAndEnforcesCooldown() {
    Instant now = Instant.parse("2026-10-30T10:00:00Z");
    OtpChallenge challenge = loginChallenge(now);
    challenge.deliver(LoginOtpChannel.EMAIL, "hash-1", now);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> challenge.deliver(LoginOtpChannel.EMAIL, "hash-2", now.plusSeconds(59)))
        .isInstanceOf(IllegalStateException.class);

    for (int resend = 1; resend < 5; resend++) {
      challenge.deliver(
          LoginOtpChannel.EMAIL, "hash-" + (resend + 1), now.plusSeconds(resend * 60L));
    }
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> challenge.deliver(LoginOtpChannel.EMAIL, "hash-6", now.plusSeconds(300)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void registrationAndResetChallengesShareEntityButKeepDistinctPurposesAndExpiry() {
    Instant now = Instant.parse("2026-10-30T10:00:00Z");
    OtpChallenge registration =
        OtpChallenge.registration(
            "reg-hash",
            "Person@Example.Test",
            LoginOtpChannel.EMAIL,
            "reg-code",
            now.plusSeconds(600));
    OtpChallenge reset =
        OtpChallenge.passwordReset(
            "reset-hash", "Person@Example.Test", "reset-code", now.plusSeconds(600));

    assertThat(registration.getPurpose()).isEqualTo(OtpChallengePurpose.REGISTRATION);
    assertThat(registration.getDestination()).isEqualTo("person@example.test");
    assertThat(reset.getPurpose()).isEqualTo(OtpChallengePurpose.PASSWORD_RESET);
    assertThat(reset.getDestination()).isEqualTo("person@example.test");
    assertThat(registration.verify("wrong-code", now.plusSeconds(1))).isFalse();
    assertThat(registration.getFailedAttempts()).isEqualTo(1);
    assertThat(reset.verify("reset-code", now.plusSeconds(1))).isTrue();
    assertThat(reset.verify("reset-code", now.plusSeconds(2))).isFalse();
  }

  private OtpChallenge loginChallenge(Instant now) {
    return OtpChallenge.login(
        "challenge-hash", UUID.randomUUID(), "127.0.0.1", now.plusSeconds(300));
  }
}
