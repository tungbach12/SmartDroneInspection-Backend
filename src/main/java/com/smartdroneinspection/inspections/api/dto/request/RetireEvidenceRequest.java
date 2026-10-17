package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MF3-02 retirement of a capture. The original bytes and metadata are kept, so the reason is part
 * of the record rather than an optional note.
 */
public record RetireEvidenceRequest(@NotBlank @Size(max = 2000) String reason) {}
