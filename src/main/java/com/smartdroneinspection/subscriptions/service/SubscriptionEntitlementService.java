package com.smartdroneinspection.subscriptions.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.subscriptions.domain.Subscription;
import com.smartdroneinspection.subscriptions.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionEntitlementService implements SubscriptionEntitlement {

  private static final String INACTIVE_MESSAGE = "An active subscription is required";

  private final SubscriptionRepository subscriptionRepository;

  public SubscriptionEntitlementService(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Override
  public void requireActive(UUID organizationId, Instant at) {
    boolean active =
        subscriptionRepository
            .findFirstByOrganizationIdAndStatus(organizationId, Subscription.Status.ACTIVE)
            .filter(subscription -> subscription.covers(at))
            .isPresent();
    if (!active) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_INACTIVE", INACTIVE_MESSAGE);
    }
  }
}
