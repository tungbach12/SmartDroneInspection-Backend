package com.smartdroneinspection.workforce.credential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only credential contract exposed to other modules.
 *
 * <p>The list finders exist because MF2-07 cannot be performed through the API without them.
 * Approval requires the reviewer to name their own credential and the Inspector's credentials, and
 * a client cannot invent those ids: asking a person to type a UUID is a form nobody completes. They
 * are scoped to one organization and, where the subject is named, to that subject, so a caller
 * cannot read another organization's or another person's workforce records.
 *
 * <p>What comes back is the summary, not the credential row. {@code evidenceId} is a reference for
 * audit and is deliberately not accompanied by any way to download the document from this contract.
 */
public interface WorkforceCredentialAccess {

  Optional<WorkforceCredentialSummary> findByIdAndOrganizationIdAndUserId(
      UUID credentialId, UUID organizationId, UUID userId);

  /** Every credential held by one person inside one organization. */
  List<WorkforceCredentialSummary> listByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  /**
   * Every credential held by one named subject inside one organization.
   *
   * <p>Used by MF2-07 to show the reviewer which credentials the assigned Inspector actually holds,
   * rather than asking them to enumerate ids.
   */
  List<WorkforceCredentialSummary> listByOrganizationIdAndSubjectId(
      UUID organizationId, UUID subjectUserId);
}
