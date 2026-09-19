package com.smartdroneinspection.inspectionrequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.domain.InspectionQuotation;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequestPriority;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionAssignmentPersistenceTest {

  @Autowired InspectionAssignmentRepository assignmentRepository;
  @Autowired InspectionRequestRepository requestRepository;
  @Autowired InspectionQuotationRepository quotationRepository;
  @Autowired InspectionServiceOrderRepository serviceOrderRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void rejectsSecondActiveAssignmentButAllowsRejectedHistory() {
    Fixture fixture = persistFixture();
    InspectionAssignment rejected =
        new InspectionAssignment(
            fixture.orderId(), fixture.inspectorId(), fixture.managerId(), Instant.now(), "Access");
    rejected.reject("Inspector unavailable");
    assignmentRepository.saveAndFlush(rejected);

    InspectionAssignment pending =
        assignmentRepository.saveAndFlush(
            new InspectionAssignment(
                fixture.orderId(),
                fixture.otherInspectorId(),
                fixture.managerId(),
                Instant.now().plusSeconds(3600),
                "Access"));

    assertThat(assignmentRepository.findByServiceOrderIdOrderByCreatedAtDesc(fixture.orderId()))
        .extracting(InspectionAssignment::getStatus)
        .containsExactlyInAnyOrder(
            InspectionAssignmentStatus.PENDING, InspectionAssignmentStatus.REJECTED);
    assertThat(pending.getStatus()).isEqualTo(InspectionAssignmentStatus.PENDING);

    assertThatThrownBy(
            () ->
                assignmentRepository.saveAndFlush(
                    new InspectionAssignment(
                        fixture.orderId(),
                        fixture.otherInspectorId(),
                        fixture.managerId(),
                        Instant.now().plusSeconds(7200),
                        "Access")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void inspectorScopedLookupDoesNotReadAnotherInspectorsAssignment() {
    Fixture fixture = persistFixture();
    InspectionAssignment assignment =
        assignmentRepository.saveAndFlush(
            new InspectionAssignment(
                fixture.orderId(),
                fixture.inspectorId(),
                fixture.managerId(),
                Instant.now(),
                "Access"));

    assertThat(
            assignmentRepository.findByIdAndInspectorUserId(
                assignment.getId(), fixture.otherInspectorId()))
        .isEmpty();
    assertThat(
            assignmentRepository
                .findByIdAndInspectorUserId(assignment.getId(), fixture.inspectorId())
                .map(InspectionAssignment::getId))
        .contains(assignment.getId());
  }

  @Test
  void rejectsSecondAcceptedAssignment() {
    Fixture fixture = persistFixture();
    InspectionAssignment accepted =
        new InspectionAssignment(
            fixture.orderId(), fixture.inspectorId(), fixture.managerId(), Instant.now(), "Access");
    accepted.accept();
    assignmentRepository.saveAndFlush(accepted);

    InspectionAssignment second =
        new InspectionAssignment(
            fixture.orderId(),
            fixture.otherInspectorId(),
            fixture.managerId(),
            Instant.now().plusSeconds(3600),
            "Access");
    second.accept();

    assertThatThrownBy(() -> assignmentRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Fixture persistFixture() {
    UUID organizationId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID otherInspectorId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID checklistTemplateId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    String suffix = organizationId.toString();

    jdbcTemplate.update(
        "INSERT INTO organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        "Organization " + suffix,
        "ORG-" + suffix);
    persistUser(clientId, "CUSTOMER_ORGANIZATION", organizationId, "client");
    persistUser(managerId, "SERVICE_WORKFORCE", null, "manager");
    persistUser(inspectorId, "SERVICE_WORKFORCE", null, "inspector");
    persistUser(otherInspectorId, "SERVICE_WORKFORCE", null, "other-inspector");
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
        clientId);
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
        clientId);
    InspectionRequest request =
        requestRepository.saveAndFlush(
            InspectionRequest.adHoc(
                organizationId,
                assetId,
                checklistTemplateId,
                clientId,
                "Ad hoc tower inspection",
                InspectionRequestPriority.NORMAL,
                null,
                null,
                null,
                null,
                null,
                null));
    InspectionQuotation quotation =
        new InspectionQuotation(
            UUID.randomUUID(),
            request.getId(),
            1,
            null,
            managerId,
            "USD",
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            BigDecimal.valueOf(100),
            "{\"items\":[]}",
            "{\"scope\":\"tower\"}",
            BigDecimal.ONE,
            "NET 30");
    quotation.send();
    quotation.approve(clientId);
    quotationRepository.saveAndFlush(quotation);
    InspectionServiceOrder order =
        serviceOrderRepository.saveAndFlush(
            InspectionServiceOrder.fromApprovedQuotation(
                quotation, "SO-" + suffix, managerId, "{\"report\":\"Final report\"}"));
    return new Fixture(order.getId(), managerId, inspectorId, otherInspectorId);
  }

  private void persistUser(UUID id, String actorZone, UUID organizationId, String label) {
    String email = label + "-" + id + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0, 0, FALSE)
        """,
        id,
        email,
        email,
        label,
        actorZone,
        organizationId);
  }

  private record Fixture(UUID orderId, UUID managerId, UUID inspectorId, UUID otherInspectorId) {}
}
