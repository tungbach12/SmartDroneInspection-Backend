package com.smartdroneinspection.assets.domain.enums;

/**
 * Lifecycle of an inspector-drone pairing for one asset (MF1-09).
 *
 * <p>The deployed table carries a partial unique index on {@code asset_id} where status is {@code
 * ACTIVE}, so an asset has at most one live pairing: starting a new one supersedes the previous
 * rather than running alongside it. That constraint is why superseding is an explicit state change
 * instead of an implicit overwrite.
 */
public enum AssetPairAssignmentStatus {
  DRAFT,
  ACTIVE,
  SUPERSEDED,
  SUSPENDED
}
