package com.smartdroneinspection.workforce.service;

import com.smartdroneinspection.workforce.credential.WorkforceCredentialAccess;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialSummary;
import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import com.smartdroneinspection.workforce.repository.WorkforceCredentialRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkforceCredentialAccessService implements WorkforceCredentialAccess {

  private final WorkforceCredentialRepository credentials;

  WorkforceCredentialAccessService(WorkforceCredentialRepository credentials) {
    this.credentials = credentials;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WorkforceCredentialSummary> findByIdAndOrganizationIdAndUserId(
      UUID credentialId, UUID organizationId, UUID userId) {
    return credentials
        .findByIdAndOrganizationIdAndUserId(credentialId, organizationId, userId)
        .map(WorkforceCredentialAccessService::toSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkforceCredentialSummary> listByOrganizationIdAndUserId(
      UUID organizationId, UUID userId) {
    return credentials
        .findByOrganizationIdAndUserIdOrderByIssuedAtDesc(organizationId, userId)
        .stream()
        .map(WorkforceCredentialAccessService::toSummary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkforceCredentialSummary> listByOrganizationIdAndSubjectId(
      UUID organizationId, UUID subjectUserId) {
    return credentials
        .findByOrganizationIdAndUserIdOrderByIssuedAtDesc(organizationId, subjectUserId)
        .stream()
        .map(WorkforceCredentialAccessService::toSummary)
        .toList();
  }

  private static WorkforceCredentialSummary toSummary(WorkforceCredential credential) {
    return new WorkforceCredentialSummary(
        credential.getId(),
        credential.getOrganizationId(),
        credential.getUserId(),
        credential.getCredentialType(),
        credential.getIssuer(),
        credential.getCredentialReference(),
        credential.getIssuedAt(),
        credential.getExpiresAt(),
        com.smartdroneinspection.workforce.credential.CredentialStatus.valueOf(
            credential.getStatus().name()),
        credential.getEvidenceId(),
        credential.getVerifiedByUserId(),
        credential.getVerifiedAt(),
        credential.getVerificationReason());
  }
}
