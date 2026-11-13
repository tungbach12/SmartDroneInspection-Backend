package com.smartdroneinspection.inspections;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MF3 fixtures: a customer owner with an assigned Inspector and an INSPECTOR who is not the
 * assignee, a second customer for cross-tenant denial, and an inspection already past field work.
 */
public final class InspectionTestFixture {

  private final UserRepository users;
  private final InspectionRepository inspections;
  private final JdbcTemplate jdbcTemplate;

  public InspectionTestFixture(
      UserRepository users, InspectionRepository inspections, JdbcTemplate jdbcTemplate) {
    this.users = users;
    this.inspections = inspections;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Data create() {
    User inspector = saveUser("mf3-inspector", UserRole.INSPECTOR);
    User reviewer = saveUser("mf3-reviewer", UserRole.CUSTOMER);
    // An INSPECTOR who is not the assignee, to prove assignment scope rather than role is enforced.
    User otherInspector = saveUser("mf3-other-inspector", UserRole.INSPECTOR);
    User outsider = saveUser("mf3-outsider", UserRole.CUSTOMER);

    UUID assetId = createAsset(reviewer.getId());
    Inspection inspection =
        inspections.saveAndFlush(
            new Inspection(reviewer.getId(), assetId, inspector.getId(), null, null, null));
    advanceToFieldCompleted(inspection);
    return new Data(
        assetId,
        inspection.getId(),
        inspector.getId(),
        reviewer.getId(),
        otherInspector.getId(),
        outsider.getId());
  }

  private void advanceToFieldCompleted(Inspection inspection) {
    jdbcTemplate.update(
        "UPDATE inspections SET status = ? WHERE id = ?",
        InspectionStatus.FIELD_COMPLETED.name(),
        inspection.getId());
  }

  private UUID createAsset(UUID ownerCustomerId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'MF3 fixture category', TRUE)
        """,
        categoryId,
        upperCode("CAT"));
    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, owner_customer_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Main span', '{"label":"District 1"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        ownerCustomerId,
        categoryId,
        upperCode("AST"));
    return assetId;
  }

  /** Codes are stored normalized: the schema rejects anything that is not upper case. */
  private static String upperCode(String prefix) {
    return (prefix + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
  }

  private User saveUser(String label, UserRole role) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            role == UserRole.INSPECTOR ? ActorZone.INSPECTOR : ActorZone.CUSTOMER);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  public record Data(
      UUID assetId,
      UUID inspectionId,
      UUID inspectorId,
      UUID reviewerId,
      UUID otherInspectorId,
      UUID outsiderId) {}
}
