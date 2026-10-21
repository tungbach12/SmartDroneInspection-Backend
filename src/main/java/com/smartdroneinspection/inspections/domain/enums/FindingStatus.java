package com.smartdroneinspection.inspections.domain.enums;

/**
 * Where a finding stands in the Inspector's review.
 *
 * <p>A finding arrives either as a detector suggestion or as a manual observation, and it stays
 * {@code PENDING} until the Inspector confirms it. {@code CONFIRMED} is the only state that makes a
 * finding official: it may enter a report, be counted, and receive a corrective outcome.
 *
 * <p>Removing a finding is not a state. It is a tombstone on the row, so a finding that was
 * confirmed and later removed still says so.
 */
public enum FindingStatus {
  PENDING,
  CONFIRMED
}
