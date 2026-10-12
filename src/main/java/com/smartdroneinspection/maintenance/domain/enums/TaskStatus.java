package com.smartdroneinspection.maintenance.domain.enums;

/** Task lifecycle from the MF4 target schema. Mirrors {@code ck_maintenance_tasks_status}. */
public enum TaskStatus {
  PLANNED,
  READY,
  IN_PROGRESS,
  WORK_COMPLETED,
  REWORK_REQUIRED,
  ACCEPTED,
  CANCELLED
}
