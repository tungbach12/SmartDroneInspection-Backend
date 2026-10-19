package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;

/** MF3-08 manual path creates an empty structured version for the author to fill in the editor. */
public record ManualReportDraftRequest(@Size(max = 10000) String narrative) {}
