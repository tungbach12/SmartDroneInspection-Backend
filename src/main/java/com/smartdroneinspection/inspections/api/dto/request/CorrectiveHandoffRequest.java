package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * MF3-12/13: closes or forwards confirmed findings for a published report.
 *
 * <p>Admin can select which confirmed findings to forward to Maintenance as repair candidates
 * (defaulting to all). If no findings are forwarded, the report remains published without handoff,
 * or an explicit no-repair rationale can close the inspection.
 */
public record CorrectiveHandoffRequest(List<UUID> findingIds, @Size(max = 2000) String rationale) {

  public CorrectiveHandoffRequest(String rationale) {
    this(null, rationale);
  }

  public CorrectiveHandoffRequest(List<UUID> findingIds) {
    this(findingIds, null);
  }
}
