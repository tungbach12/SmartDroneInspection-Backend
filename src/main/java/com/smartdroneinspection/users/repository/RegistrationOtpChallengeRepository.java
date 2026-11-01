package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.RegistrationOtpChallenge;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationOtpChallengeRepository
    extends JpaRepository<RegistrationOtpChallenge, UUID> {

  Optional<RegistrationOtpChallenge> findByChallengeHash(String challengeHash);

  int countByDestinationAndCreatedAtAfter(String destination, Instant since);
}
