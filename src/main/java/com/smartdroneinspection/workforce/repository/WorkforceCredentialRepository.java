package com.smartdroneinspection.workforce.repository;

import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceCredentialRepository extends JpaRepository<WorkforceCredential, UUID> {

  /** Organization- and subject-scoped lookup used by MF2 and MF4. */
  List<WorkforceCredential> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  Optional<WorkforceCredential> findByIdAndOrganizationIdAndUserId(
      UUID id, UUID organizationId, UUID userId);

  /** Newest first for readiness review sources. */
  List<WorkforceCredential> findByOrganizationIdAndUserIdOrderByIssuedAtDesc(
      UUID organizationId, UUID userId);
}
