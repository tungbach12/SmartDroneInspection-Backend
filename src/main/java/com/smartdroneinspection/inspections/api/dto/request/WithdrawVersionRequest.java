package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;

/** MF3-08 author withdrawal of a submission the reviewer has not acted on yet. */
public record WithdrawVersionRequest(@Size(max = 2000) String reason) {}
