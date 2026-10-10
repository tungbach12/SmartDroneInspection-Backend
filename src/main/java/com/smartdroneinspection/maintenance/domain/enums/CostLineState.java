package com.smartdroneinspection.maintenance.domain.enums;

/**
 * Which budget a cost line belongs to.
 *
 * <p>A line is never silently moved from one state to another: an estimate line is not an actual
 * cost, and MF4-09 onwards records actuals separately.
 */
public enum CostLineState {
  ESTIMATE,
  CHANGE,
  ACTUAL
}
