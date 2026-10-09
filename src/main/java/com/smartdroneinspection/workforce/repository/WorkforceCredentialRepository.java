package com.smartdroneinspection.workforce.repository;

import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceCredentialRepository extends JpaRepository<WorkforceCredential, UUID> {

  Optional<WorkforceCredential> findByIdAndOrganizationIdAndUserId(
      UUID id, UUID organizationId, UUID userId);
}
