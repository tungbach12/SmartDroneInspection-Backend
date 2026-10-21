package com.smartdroneinspection.subscriptions.repository;

import com.smartdroneinspection.subscriptions.domain.Subscription;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findFirstByOrganizationIdAndStatus(
      UUID organizationId, Subscription.Status status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select subscription from Subscription subscription "
          + "where subscription.organizationId = :organizationId "
          + "and subscription.status = com.smartdroneinspection.subscriptions.domain.Subscription.Status.ACTIVE")
  Optional<Subscription> findActiveForUpdate(@Param("organizationId") UUID organizationId);
}
