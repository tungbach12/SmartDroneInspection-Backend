package com.smartdroneinspection.workforce.repository;

import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceCredentialRepository extends JpaRepository<WorkforceCredential, UUID> {

  Optional<WorkforceCredential> findByIdAndOrganizationIdAndUserId(
      UUID id, UUID organizationId, UUID userId);

  /**
   * Newest first, so a reviewer sees a person's current credential before an expired one rather
   * than having to judge which of the two applies.
   */
  List<WorkforceCredential> findByOrganizationIdAndUserIdOrderByIssuedAtDesc(
      UUID organizationId, UUID userId);
}
