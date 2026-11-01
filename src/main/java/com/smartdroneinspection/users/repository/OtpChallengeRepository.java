package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.OtpChallenge;
import com.smartdroneinspection.users.domain.enums.OtpChallengePurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

  long countByPurposeAndUserIdAndCreatedAtAfter(
      OtpChallengePurpose purpose, UUID userId, Instant createdAfter);

  long countByPurposeAndClientIpAndCreatedAtAfter(
      OtpChallengePurpose purpose, String clientIp, Instant createdAfter);

  long countByPurposeAndDestinationAndCreatedAtAfter(
      OtpChallengePurpose purpose, String destination, Instant createdAfter);

  Optional<OtpChallenge> findByChallengeHashAndPurpose(
      String challengeHash, OtpChallengePurpose purpose);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from OtpChallenge c where c.challengeHash = :hash and c.purpose = :purpose")
  Optional<OtpChallenge> findForUpdateByChallengeHashAndPurpose(
      @Param("hash") String hash, @Param("purpose") OtpChallengePurpose purpose);
}
