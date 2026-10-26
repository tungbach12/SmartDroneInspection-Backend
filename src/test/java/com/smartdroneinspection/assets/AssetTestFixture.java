package com.smartdroneinspection.assets;

import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Users and category shared by the asset tests. */
public final class AssetTestFixture {

  private final AssetCategoryRepository categories;
  private final AssetRepository assets;
  private final UserRepository users;
  private final JdbcTemplate jdbcTemplate;

  public AssetTestFixture(
      AssetCategoryRepository categories,
      AssetRepository assets,
      UserRepository users,
      JdbcTemplate jdbcTemplate) {
    this.categories = categories;
    this.assets = assets;
    this.users = users;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Data create() {
    UUID organizationId = createOrganization("Primary");
    UUID otherOrganizationId = createOrganization("Other");

    User client =
        saveUser(
            "client",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId,
            UserRole.ORG_MANAGER);
    User otherClient =
        saveUser(
            "other-client",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            otherOrganizationId,
            UserRole.ORG_MANAGER);
    User manager =
        saveUser(
            "manager",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId,
            UserRole.ORG_MANAGER);
    User admin = saveUser("admin", UserStatus.ACTIVE, ActorZone.PLATFORM, null, UserRole.ADMIN);

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory("bridge-" + organizationId, "Bridge", "Bridge infrastructure", true));

    return new Data(
        organizationId,
        otherOrganizationId,
        client.getId(),
        otherClient.getId(),
        manager.getId(),
        admin.getId(),
        category.getId());
  }

  private UUID createOrganization(String label) {
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        label + " organization " + organizationId,
        label + " organization " + organizationId,
        "ORG-" + organizationId);
    return organizationId;
  }

  public void grantEntitlement(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at)
        VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        organizationId);
  }

  private User saveUser(
      String label, UserStatus status, ActorZone actorZone, UUID organizationId, UserRole role) {
    UUID id = UUID.randomUUID();
    String email = label + "-" + id + "@example.test";
    User user = new User(email, label, "{noop}password", status, actorZone, organizationId);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  public record Data(
      UUID organizationId,
      UUID otherOrganizationId,
      UUID clientId,
      UUID otherClientId,
      UUID managerId,
      UUID adminId,
      UUID categoryId) {}
}
