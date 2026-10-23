package com.smartdroneinspection.inspections.api.dto.request;

import java.util.List;
import java.util.UUID;

/**
 * MF3-02 bulk retirement of captures. When an entire flight session or batch (e.g. up to hundreds
 * or thousands of captures) is determined to be the wrong site or invalid, this operation retires
 * the target set while preserving original bytes.
 *
 * <p>If {@code evidenceIds} is empty or null, all currently active (unretired) captures for the
 * inspection are retired.
 */
public record BulkRetireEvidenceRequest(List<UUID> evidenceIds) {}
