package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * MF3-12: the organization reviewer records a scoped corrective action after report publication.
 */
public record CorrectiveActionRequest(
    boolean correctiveActionRequired,
    @Size(max = 24) String priority,
    @Future Instant dueAt,
    @NotBlank @Size(max = 4000) String rationale) {

  @AssertTrue(message = "Priority and due date are only valid when corrective action is required")
  public boolean isCorrectiveActionConsistent() {
    return correctiveActionRequired || (priority == null && dueAt == null);
  }
}
