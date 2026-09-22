package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.repository.ChecklistResponseRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionWorkflowTest {

  @Autowired InspectionService inspectionService;
  @Autowired InspectionRepository inspections;
  @Autowired ChecklistResponseRepository responses;
  @Autowired ObjectMapper objectMapper;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired InspectionRequestRepository requests;
  @Autowired InspectionQuotationRepository quotations;
  @Autowired InspectionServiceOrderRepository serviceOrders;
  @Autowired InspectionAssignmentRepository assignments;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void listsOnlyAcceptedAssignmentsForTheActiveAssignee() {
    InspectionFixture.Data data = fixture().create();

    assertThat(inspectionService.listAcceptedAssignments(data.inspectorId()))
        .extracting(response -> response.assignmentId())
        .containsExactly(data.assignmentId());
    assertThat(inspectionService.listAcceptedAssignments(data.otherInspectorId())).isEmpty();
    assertThatThrownBy(() -> inspectionService.listAcceptedAssignments(data.inactiveInspectorId()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_SCOPE_DENIED"));
  }

  @Test
  void retriesStartWithoutCreatingAnotherInspection() {
    InspectionFixture.Data data = fixture().create();

    var starts =
        IntStream.range(0, 5)
            .mapToObj(index -> inspectionService.start(data.inspectorId(), data.assignmentId()))
            .toList();

    assertThat(starts.stream().map(response -> response.inspectionId()).distinct()).hasSize(1);
    assertThat(starts).allMatch(response -> response.status().name().equals("IN_PROGRESS"));
    assertThat(inspections.findAll()).hasSize(1);
  }

  @Test
  void deniesNonAssigneeAndInactiveInspectorStart() {
    InspectionFixture.Data data = fixture().create();

    assertThatThrownBy(() -> inspectionService.start(data.otherInspectorId(), data.assignmentId()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_SCOPE_DENIED"));
    assertThatThrownBy(
            () -> inspectionService.start(data.inactiveInspectorId(), data.assignmentId()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_SCOPE_DENIED"));
    assertThat(inspections.findAll()).isEmpty();
  }

  @Test
  void storesValidResponseAndRejectsBlankOrForeignItems() throws Exception {
    InspectionFixture.Data data = fixture().create();
    var started = inspectionService.start(data.inspectorId(), data.assignmentId());
    JsonNode validValue = objectMapper.readTree("{\"value\":\"PASS\"}");

    var saved =
        inspectionService.saveChecklistResponse(
            data.inspectorId(),
            started.inspectionId(),
            data.checklistItemId(),
            new ChecklistResponseRequest(validValue, "No visible damage"));

    assertThat(saved.completedByUserId()).isEqualTo(data.inspectorId());
    assertThat(saved.completedAt()).isNotNull();
    assertThat(
            responses.findByInspectionIdAndChecklistItemId(
                started.inspectionId(), data.checklistItemId()))
        .isPresent();

    JsonNode blankValue = objectMapper.readTree("{\"value\":\"\"}");
    assertThatThrownBy(
            () ->
                inspectionService.saveChecklistResponse(
                    data.inspectorId(),
                    started.inspectionId(),
                    data.checklistItemId(),
                    new ChecklistResponseRequest(blankValue, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("CHECKLIST_RESPONSE_INVALID"));

    assertThatThrownBy(
            () ->
                inspectionService.saveChecklistResponse(
                    data.inspectorId(),
                    started.inspectionId(),
                    UUID.randomUUID(),
                    new ChecklistResponseRequest(validValue, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("CHECKLIST_RESPONSE_INVALID"));
    assertThat(responses.findAll()).hasSize(1);
  }

  private InspectionFixture fixture() {
    return new InspectionFixture(
        categories,
        templates,
        assets,
        requests,
        quotations,
        serviceOrders,
        assignments,
        users,
        jdbcTemplate);
  }
}
