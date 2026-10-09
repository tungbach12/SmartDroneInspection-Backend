package com.smartdroneinspection.subscriptions.repository;

import com.smartdroneinspection.subscriptions.domain.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findFirstByOrganizationIdAndStatus(
      UUID organizationId, Subscription.Status status);
}
