package com.smartdroneinspection.assets.domain.enums;

/**
 * Whether a drone may be assigned to a field mission.
 *
 * <p>MF1-06 records this on the drone and MF2 uses it when an administrator pairs the drone with an
 * inspector, so the vocabulary is deliberately narrower than a generic asset status: only {@code
 * ACTIVE} drones are eligible for a pairing.
 */
public enum DroneServiceability {
  ACTIVE,
  MAINTENANCE,
  SUSPENDED,
  RETIRED
}
