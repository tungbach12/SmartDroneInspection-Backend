package com.smartdroneinspection.assets.domain.enums;

/**
 * The states a customer organization may record a permit in.
 *
 * <p>Every value is a statement the organization makes about itself. None of them is a
 * determination made by a regulator or an authority, and {@code NOT_APPLICABLE} in particular
 * records the organization's own conclusion that no permit applies — it is not a regulator's ruling
 * that one does not apply.
 */
public enum PermitStatus {
  APPLICATION,
  ACTIVE,
  EXPIRED,
  REJECTED,
  REVOKED,
  NOT_APPLICABLE
}
