package com.smartdroneinspection.inspections.api.dto.request;

/** Explicit acknowledgment for a clean inspection; ignored when findings exist. */
public record ConfirmFindingsRequest(boolean noFindingsAcknowledged) {}
