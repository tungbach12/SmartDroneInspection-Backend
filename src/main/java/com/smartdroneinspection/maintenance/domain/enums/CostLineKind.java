package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Cost classification from the MF4 target schema. Mirrors {@code ck_maintenance_cost_lines_kind}.
 */
public enum CostLineKind {
  LABOR,
  MATERIAL,
  EQUIPMENT,
  EXTERNAL_SERVICE,
  OTHER,
  CONTINGENCY
}
