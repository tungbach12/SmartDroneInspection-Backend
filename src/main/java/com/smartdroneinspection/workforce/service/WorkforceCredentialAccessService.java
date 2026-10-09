package com.smartdroneinspection.workforce.service;

import com.smartdroneinspection.workforce.credential.WorkforceCredentialAccess;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialSummary;
import com.smartdroneinspection.workforce.repository.WorkforceCredentialRepository;
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
        .map(
            credential ->
                new WorkforceCredentialSummary(
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
                    credential.getVerificationReason()));
  }
}
