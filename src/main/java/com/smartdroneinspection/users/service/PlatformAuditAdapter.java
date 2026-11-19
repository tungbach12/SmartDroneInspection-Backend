package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.api.PlatformAuditPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Exposes the users-owned security audit writer to other modules through {@link PlatformAuditPort}.
 */
@Component
public class PlatformAuditAdapter implements PlatformAuditPort {

  private final SecurityAuditService audit;

  public PlatformAuditAdapter(SecurityAuditService audit) {
    this.audit = audit;
  }

  @Override
  public void recordPlatformAction(
      UUID actorUserId,
      String aggregateType,
      UUID aggregateId,
      String action,
      String outcome,
      String detail) {
    audit.recordPlatformAction(actorUserId, aggregateType, aggregateId, action, outcome, detail);
  }
}
