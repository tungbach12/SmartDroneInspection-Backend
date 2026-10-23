package com.smartdroneinspection.subscriptions.repository;

import com.smartdroneinspection.subscriptions.domain.Subscription;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Subscription> findFirstByOrganizationIdAndStatus(
      UUID organizationId, Subscription.Status status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select s from Subscription s where s.organizationId = :organizationId and s.status = 'ACTIVE'")
  Optional<Subscription> findActiveForUpdate(
      @org.springframework.data.repository.query.Param("organizationId") UUID organizationId);

  List<Subscription> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  Optional<Subscription> findFirstByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<Subscription> findByStatusOrderByCreatedAtDesc(Subscription.Status status);

  List<Subscription> findAllByOrderByCreatedAtDesc();
}
