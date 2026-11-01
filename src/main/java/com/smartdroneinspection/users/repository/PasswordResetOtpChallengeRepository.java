package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.PasswordResetOtpChallenge;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetOtpChallengeRepository
    extends JpaRepository<PasswordResetOtpChallenge, UUID> {

  Optional<PasswordResetOtpChallenge> findByChallengeHash(String challengeHash);

  int countByEmailAndCreatedAtAfter(String email, Instant since);
}
