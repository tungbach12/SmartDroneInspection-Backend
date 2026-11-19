package com.smartdroneinspection.shared.api;

import java.util.UUID;

/**
 * Cross-module boundary for recording privileged platform actions without exposing the users
 * module's internal audit service.
 */
public interface PlatformAuditPort {

  void recordPlatformAction(
      UUID actorUserId,
      String aggregateType,
      UUID aggregateId,
      String action,
      String outcome,
      String detail);
}
