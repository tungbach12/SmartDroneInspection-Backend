package com.smartdroneinspection.workforce.repository;

import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceCredentialRepository extends JpaRepository<WorkforceCredential, UUID> {

  /**
   * Organization-scoped read. MF4 must never inspect a credential belonging to another tenant, so
   * every lookup carries the organization rather than filtering in memory.
   */
  List<WorkforceCredential> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
