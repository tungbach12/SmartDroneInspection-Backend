package com.smartdroneinspection.assets;

import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Users, category, and checklist template shared by the WF1 asset tests. */
public final class AssetTestFixture {

  private final AssetCategoryRepository categories;
  private final ChecklistTemplateRepository templates;
  private final AssetRepository assets;
  private final UserRepository users;
  private final JdbcTemplate jdbcTemplate;

  public AssetTestFixture(
      AssetCategoryRepository categories,
      ChecklistTemplateRepository templates,
      AssetRepository assets,
      UserRepository users,
      JdbcTemplate jdbcTemplate) {
    this.categories = categories;
    this.templates = templates;
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
            UserRole.ORG_ADMIN);
    User otherClient =
        saveUser(
            "other-client",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            otherOrganizationId,
            UserRole.ORG_ADMIN);
    User manager =
        saveUser(
            "manager",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId,
            UserRole.ORG_ADMIN);
    User admin = saveUser("admin", UserStatus.ACTIVE, ActorZone.PLATFORM, null, UserRole.ADMIN);

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory("bridge-" + organizationId, "Bridge", "Bridge infrastructure", true));
    ChecklistTemplate template =
        new ChecklistTemplate(
            "bridge-inspection-" + organizationId,
            1,
            category.getId(),
            "Bridge inspection",
            "WF1 fixture checklist",
            manager.getId());
    template.addItem(
        "surface-condition",
        "Surface",
        "Check the visible surface condition",
        ChecklistResponseType.PASS_FAIL,
        true,
        0,
        null,
        null);
    template.publish();
    templates.saveAndFlush(template);

    return new Data(
        organizationId,
        otherOrganizationId,
        client.getId(),
        otherClient.getId(),
        manager.getId(),
        admin.getId(),
        category.getId(),
        template.getId());
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

  private User saveUser(
      String label, UserStatus status, ActorZone actorZone, UUID organizationId, UserRole role) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            status,
            actorZone,
            organizationId);
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
      UUID categoryId,
      UUID checklistTemplateId) {}
}
