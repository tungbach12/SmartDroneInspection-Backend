package com.smartdroneinspection.subscriptions;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SubscriptionEntitlementServiceTest {

  private static final Instant AT = Instant.parse("2026-10-08T12:00:00Z");

  @Autowired SubscriptionEntitlement entitlement;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void allowsOnlyWhenActiveSubscriptionCoversInclusiveStartAndExclusiveEnd() {
    UUID organizationId = persistOrganization();
    insertSubscription(organizationId, "ACTIVE", AT, AT.plusSeconds(3600));

    assertThatNoException().isThrownBy(() -> entitlement.requireActive(organizationId, AT));
    assertInactive(organizationId, AT.plusSeconds(3600));
  }

  @Test
  void deniesExpiredSuspendedAndMissingSubscriptions() {
    UUID expiredOrganizationId = persistOrganization();
    insertSubscription(
        expiredOrganizationId, "ACTIVE", AT.minusSeconds(7200), AT.minusSeconds(3600));
    UUID suspendedOrganizationId = persistOrganization();
    insertSubscription(
        suspendedOrganizationId, "SUSPENDED", AT.minusSeconds(3600), AT.plusSeconds(3600));
    UUID missingOrganizationId = persistOrganization();

    assertInactive(expiredOrganizationId, AT);
    assertInactive(suspendedOrganizationId, AT);
    assertInactive(missingOrganizationId, AT);
  }

  @Test
  void doesNotUseAnotherOrganizationsActiveSubscription() {
    UUID subscribedOrganizationId = persistOrganization();
    insertSubscription(subscribedOrganizationId, "ACTIVE", AT.minusSeconds(60), AT.plusSeconds(60));
    UUID otherOrganizationId = persistOrganization();

    assertThatNoException()
        .isThrownBy(() -> entitlement.requireActive(subscribedOrganizationId, AT));
    assertInactive(otherOrganizationId, AT);
  }

  @Test
  void treatsNullSubscriptionBoundsAsUnbounded() {
    UUID organizationId = persistOrganization();
    insertSubscription(organizationId, "ACTIVE", null, null);

    assertThatNoException().isThrownBy(() -> entitlement.requireActive(organizationId, AT));
  }

  private void assertInactive(UUID organizationId, Instant at) {
    assertThatThrownBy(() -> entitlement.requireActive(organizationId, at))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            failure -> {
              BusinessException exception = (BusinessException) failure;
              org.assertj.core.api.Assertions.assertThat(exception.status())
                  .isEqualTo(HttpStatus.FORBIDDEN);
              org.assertj.core.api.Assertions.assertThat(exception.code())
                  .isEqualTo("SUBSCRIPTION_INACTIVE");
              org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                  .isEqualTo("An active subscription is required");
            });
  }

  private UUID persistOrganization() {
    UUID organizationId = UUID.randomUUID();
    String suffix = organizationId.toString();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'UTC', 'ACTIVE')
        """,
        organizationId,
        "Entitlement organization " + suffix,
        "Entitlement organization " + suffix,
        "ENT-" + suffix);
    return organizationId;
  }

  private void insertSubscription(
      UUID organizationId, String status, Instant startsAt, Instant endsAt) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at, ends_at)
        VALUES (?, 'ENTERPRISE', 1, ?, ?, ?)
        """,
        organizationId,
        status,
        startsAt == null ? null : Timestamp.from(startsAt),
        endsAt == null ? null : Timestamp.from(endsAt));
  }
}
