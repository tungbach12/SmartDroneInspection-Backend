package com.smartdroneinspection.assets.domain.enums;

/**
 * The assigned inspector's answer to a pairing (MF2-01).
 *
 * <p>Distinct from {@link AssetPairAssignmentStatus}: the status tracks whether the pairing is
 * still live, this records the answer the inspector gave about it. An accepted pairing is still not
 * a ready mission - MF2-07 decides that separately.
 */
public enum AssignmentResponse {
  ACCEPTED,
  REJECTED
}
