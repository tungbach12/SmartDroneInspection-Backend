package com.smartdroneinspection.subscriptions;

import java.time.Instant;
import java.util.UUID;

/** Read-only tenant subscription entitlement boundary for consuming modules. */
public interface SubscriptionEntitlement {

  void requireActive(UUID organizationId, Instant at);
}
