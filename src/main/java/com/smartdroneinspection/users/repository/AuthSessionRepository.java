package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.AuthSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

  List<AuthSession> findByUser_IdAndRevokedAtIsNull(UUID userId);
}
