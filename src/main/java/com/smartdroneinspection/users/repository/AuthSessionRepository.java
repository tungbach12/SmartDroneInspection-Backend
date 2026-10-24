package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.AuthSession;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

  List<AuthSession> findByUser_IdAndRevokedAtIsNull(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "user")
  @Query(
      "select s from AuthSession s where s.tokenHash = :tokenHash or s.previousTokenHash = :tokenHash")
  Optional<AuthSession> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);
}
