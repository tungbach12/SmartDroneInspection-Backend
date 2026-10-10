package com.smartdroneinspection.assets.domain.enums;

/**
 * Lifecycle of an aviation permit or approval record (MF1-07, MF2-04).
 *
 * <p>The vocabulary deliberately distinguishes <em>not yet granted</em> from <em>not required</em>.
 * {@code APPLICATION} is a request an authority has not decided on; {@code NOT_APPLICABLE} records
 * that no permit is legally required for the mission. MF2-05 blocks readiness when the required
 * authorization is missing, and an internal approval must never convert a missing permit into a
 * cleared one - so those two states must stay distinguishable all the way to the readiness gate.
 */
public enum FlightPermitStatus {
  APPLICATION,
  ACTIVE,
  EXPIRED,
  REJECTED,
  REVOKED,
  NOT_APPLICABLE
}
