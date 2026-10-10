package com.smartdroneinspection.workforce.credential;

import java.util.Optional;
import java.util.UUID;

/** Read-only credential contract exposed to other modules. */
public interface WorkforceCredentialAccess {

  Optional<WorkforceCredentialSummary> findByIdAndOrganizationIdAndUserId(
      UUID credentialId, UUID organizationId, UUID userId);
}
