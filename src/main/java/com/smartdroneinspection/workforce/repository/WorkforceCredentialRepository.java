package com.smartdroneinspection.workforce.repository;

import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkforceCredentialRepository extends JpaRepository<WorkforceCredential, UUID> {

  /** Tenant-scoped list used by the MF1 credential-management API. */
  @Query(
      "select c from WorkforceCredential c where c.organizationId = :organizationId"
          + " order by c.createdAt desc")
  List<WorkforceCredential> listByOrganization(@Param("organizationId") UUID organizationId);

  /** Tenant-scoped lookup used by MF1 credential review. */
  @Query(
      "select c from WorkforceCredential c where c.id = :id and c.organizationId = :organizationId")
  Optional<WorkforceCredential> findInOrganization(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  /** Organization- and subject-scoped lookup used by MF2 and MF4. */
  List<WorkforceCredential> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  Optional<WorkforceCredential> findByIdAndOrganizationIdAndUserId(
      UUID id, UUID organizationId, UUID userId);

  /** Newest first for readiness review sources. */
  List<WorkforceCredential> findByOrganizationIdAndUserIdOrderByIssuedAtDesc(
      UUID organizationId, UUID userId);
}
