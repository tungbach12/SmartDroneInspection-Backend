package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.LoginOtpChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginOtpChallengeRepository extends JpaRepository<LoginOtpChallenge, UUID> {

  long countByUserIdAndCreatedAtAfter(UUID userId, Instant createdAfter);

  long countByClientIpAndCreatedAtAfter(String clientIp, Instant createdAfter);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from LoginOtpChallenge c where c.challengeHash = :hash")
  Optional<LoginOtpChallenge> findForUpdateByChallengeHash(@Param("hash") String hash);
}
