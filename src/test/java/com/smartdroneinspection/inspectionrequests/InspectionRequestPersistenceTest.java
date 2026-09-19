package com.smartdroneinspection.inspectionrequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionRequestPriority;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionRequestPersistenceTest {

  private static final String CHECKSUM = "a".repeat(64);

  @Autowired InspectionRequestRepository requestRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void returnsRequestOnlyForItsOrganization() {
    Fixture fixture = persistFixture();
    InspectionRequest request = requestRepository.saveAndFlush(newAdHocRequest(fixture));

    assertThat(
            requestRepository
                .findByIdAndOrganizationId(request.getId(), fixture.organizationId())
                .map(InspectionRequest::getId))
        .contains(request.getId());
    assertThat(requestRepository.findByIdAndOrganizationId(request.getId(), UUID.randomUUID()))
        .isEmpty();
  }

  @Test
  void rejectsDuplicatePeriodicDueCycle() {
    Fixture fixture = persistFixture();
    LocalDate dueCycle = LocalDate.of(2026, 9, 1);
    requestRepository.saveAndFlush(periodicRequest(fixture, dueCycle));

    assertThatThrownBy(() -> requestRepository.saveAndFlush(periodicRequest(fixture, dueCycle)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void allowsAdHocRequestAlongsidePeriodicDueCycle() {
    Fixture fixture = persistFixture();
    requestRepository.saveAndFlush(periodicRequest(fixture, LocalDate.of(2026, 9, 1)));

    InspectionRequest adHoc = requestRepository.saveAndFlush(newAdHocRequest(fixture));

    assertThat(adHoc.getId()).isNotNull();
  }

  @Test
  void rejectsDuplicateAttachmentChecksumWithinRequest() {
    Fixture fixture = persistFixture();
    InspectionRequest request = newAdHocRequest(fixture);
    request.addAttachment(
        fixture.userId(), "first.jpg", "image/jpeg", 100, CHECKSUM, "requests/first.jpg", null);
    request.addAttachment(
        fixture.userId(), "second.jpg", "image/jpeg", 200, CHECKSUM, "requests/second.jpg", null);

    assertThatThrownBy(() -> requestRepository.saveAndFlush(request))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDuplicateAttachmentObjectKeyAcrossRequests() {
    Fixture fixture = persistFixture();
    InspectionRequest first = newAdHocRequest(fixture);
    first.addAttachment(
        fixture.userId(), "first.jpg", "image/jpeg", 100, CHECKSUM, "requests/shared.jpg", null);
    requestRepository.saveAndFlush(first);

    InspectionRequest second = newAdHocRequest(fixture);
    second.addAttachment(
        fixture.userId(),
        "second.jpg",
        "image/jpeg",
        100,
        "b".repeat(64),
        "requests/shared.jpg",
        null);

    assertThatThrownBy(() -> requestRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private InspectionRequest periodicRequest(Fixture fixture, LocalDate dueCycle) {
    return InspectionRequest.periodic(
        fixture.organizationId(),
        fixture.assetId(),
        fixture.scheduleId(),
        fixture.checklistTemplateId(),
        dueCycle,
        fixture.userId(),
        "Periodic tower inspection",
        InspectionRequestPriority.NORMAL,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private InspectionRequest newAdHocRequest(Fixture fixture) {
    return InspectionRequest.adHoc(
        fixture.organizationId(),
        fixture.assetId(),
        fixture.checklistTemplateId(),
        fixture.userId(),
        "Ad hoc tower inspection",
        InspectionRequestPriority.HIGH,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private Fixture persistFixture() {
    UUID organizationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID checklistTemplateId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID scheduleId = UUID.randomUUID();
    String suffix = organizationId.toString();

    jdbcTemplate.update(
        "INSERT INTO organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        "Organization " + suffix,
        "ORG-" + suffix);
    String email = "request-user-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?, 0, 0, FALSE)
        """,
        userId,
        email,
        email,
        "Request User",
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO asset_categories (id, code, name) VALUES (?, ?, ?)",
        categoryId,
        "CATEGORY-" + suffix.toUpperCase(Locale.ROOT),
        "Category " + suffix);
    jdbcTemplate.update(
        """
        INSERT INTO checklist_templates (
          id, template_key, version_number, asset_category_id, name, status, created_by_user_id
        ) VALUES (?, ?, 1, ?, ?, 'DRAFT', ?)
        """,
        checklistTemplateId,
        "CHECKLIST-" + suffix.toUpperCase(Locale.ROOT),
        categoryId,
        "Bridge Checklist",
        userId);
    jdbcTemplate.update(
        """
        INSERT INTO assets (
          id, organization_id, category_id, code, name, location_text,
          status, created_by_user_id
        ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
        """,
        assetId,
        organizationId,
        categoryId,
        "ASSET-" + suffix.toUpperCase(Locale.ROOT),
        "Bridge",
        "District 1",
        userId);
    jdbcTemplate.update(
        """
        INSERT INTO inspection_schedules (
          id, asset_id, checklist_template_id, frequency_unit, frequency_interval,
          next_due_at, status, created_by_user_id
        ) VALUES (?, ?, ?, 'MONTH', 1, ?, 'ACTIVE', ?)
        """,
        scheduleId,
        assetId,
        checklistTemplateId,
        OffsetDateTime.parse("2026-10-01T00:00:00Z"),
        userId);
    return new Fixture(organizationId, userId, checklistTemplateId, assetId, scheduleId);
  }

  private record Fixture(
      UUID organizationId, UUID userId, UUID checklistTemplateId, UUID assetId, UUID scheduleId) {}
}
