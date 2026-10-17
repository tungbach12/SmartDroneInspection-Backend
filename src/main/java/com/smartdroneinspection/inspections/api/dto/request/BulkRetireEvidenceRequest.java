package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * MF3-02 bulk retirement of captures. When an entire flight session or batch (e.g. up to hundreds
 * or thousands of captures) is determined to be the wrong site or invalid, this operation records a
 * single shared reason across the target set while preserving all bytes and checksums for audit.
 *
 * <p>If {@code evidenceIds} is empty or null, all currently active (unretired) captures for the
 * inspection are retired.
 */
public record BulkRetireEvidenceRequest(
    List<UUID> evidenceIds, @NotBlank @Size(max = 2000) String reason) {}
