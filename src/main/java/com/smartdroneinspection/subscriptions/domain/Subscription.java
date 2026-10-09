package com.smartdroneinspection.subscriptions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "subscriptions")
public class Subscription {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24, updatable = false)
  private Status status;

  @Column(name = "starts_at", updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", updatable = false)
  private Instant endsAt;

  protected Subscription() {}

  public boolean covers(Instant at) {
    return status == Status.ACTIVE
        && (startsAt == null || !startsAt.isAfter(at))
        && (endsAt == null || at.isBefore(endsAt));
  }

  public enum Status {
    PENDING,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    EXPIRED,
    CANCELLED
  }
}
