package com.smartdroneinspection.assets.domain.enums;

/**
 * Maintenance state of an airframe. MF2 offers only ACTIVE drones for pairing, but ACTIVE is not a
 * permit or flight-readiness claim: an airframe may be serviceable and still have no valid permit.
 */
public enum DroneServiceability {
  ACTIVE,
  MAINTENANCE,
  SUSPENDED,
  RETIRED
}
