package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MF3-08 manual authoring. Report 3 requires that a failed or unconfigured drafting service still
 * lets the assigned Inspector complete a structured draft under the same review gates, so the
 * author supplies the narrative and the system records that no automated drafting contributed to
 * it.
 */
public record ManualReportDraftRequest(
    @NotBlank @Size(max = 10000) String narrative, @Size(max = 2000) String omissionDisclosure) {}
