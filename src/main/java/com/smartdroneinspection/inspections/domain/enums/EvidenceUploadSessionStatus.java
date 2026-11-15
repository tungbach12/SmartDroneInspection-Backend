package com.smartdroneinspection.inspections.domain.enums;

/** Lifecycle of a chunked resumable evidence upload session. */
public enum EvidenceUploadSessionStatus {
  OPEN,
  COMPLETING,
  PROCESSING,
  READY,
  FAILED,
  CANCELLED,
  EXPIRED
}
