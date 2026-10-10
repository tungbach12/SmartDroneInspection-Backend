package com.smartdroneinspection.workforce;

import com.smartdroneinspection.workforce.domain.CredentialStatus;
import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import com.smartdroneinspection.workforce.repository.WorkforceCredentialRepository;
import com.smartdroneinspection.workforce.spi.WorkforceAccess;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link WorkforceAccess}, reading the credential table scoped by organization. */
@Service
public class WorkforceAccessAdapter implements WorkforceAccess {

  private final WorkforceCredentialRepository credentials;

  public WorkforceAccessAdapter(WorkforceCredentialRepository credentials) {
    this.credentials = credentials;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean currentlyQualifies(UUID organizationId, UUID userId) {
    Instant now = Instant.now();
    for (WorkforceCredential credential :
        credentials.findByOrganizationIdAndUserId(organizationId, userId)) {
      // A credential still in review is not yet a verdict, so it neither qualifies nor blocks.
      if (credential.getStatus() == CredentialStatus.DRAFT
          || credential.getStatus() == CredentialStatus.PENDING_REVIEW) {
        continue;
      }
      if (!credential.qualifiesAt(now)) {
        return false;
      }
    }
    return true;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CredentialView> findCredentials(UUID organizationId, UUID userId) {
    return credentials.findByOrganizationIdAndUserId(organizationId, userId).stream()
        .map(
            credential ->
                new CredentialView(
                    credential.getUserId(),
                    credential.getCredentialType(),
                    credential.getStatus(),
                    credential.getExpiresAt()))
        .toList();
  }
}
