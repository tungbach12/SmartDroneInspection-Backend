package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.ChecklistItem;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.ChecklistResponseRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

  @Mock InspectionAssignmentRepository assignments;
  @Mock InspectionServiceOrderRepository serviceOrders;
  @Mock InspectionRequestRepository requests;
  @Mock AssetRepository assets;
  @Mock ChecklistTemplateRepository templates;
  @Mock InspectionRepository inspections;
  @Mock ChecklistResponseRepository responses;
  @Mock UserAccess users;
  @Mock InspectionAssignment assignment;
  @Mock ChecklistTemplate template;
  @Mock ChecklistItem item;

  private final UUID inspectorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();
  private final UUID templateId = UUID.randomUUID();
  private final UUID checklistItemId = UUID.randomUUID();
  private Inspection inspection;
  private InspectionService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    objectMapper = new ObjectMapper();
    service =
        new InspectionService(
            assignments,
            serviceOrders,
            requests,
            assets,
            templates,
            inspections,
            responses,
            users,
            objectMapper);
    inspection =
        new Inspection(UUID.randomUUID(), assignmentId, UUID.randomUUID(), inspectorId, templateId);
    inspection.start();
    when(users.findActiveUser(inspectorId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(inspectorId, Set.of("INSPECTOR"))));
    when(inspections.findForUpdateByIdAndAuthorUserId(inspectionId, inspectorId))
        .thenReturn(Optional.of(inspection));
    when(assignment.getStatus()).thenReturn(InspectionAssignmentStatus.ACCEPTED);
    when(assignments.findByIdAndInspectorUserId(assignmentId, inspectorId))
        .thenReturn(Optional.of(assignment));
  }

  @Test
  void checklistResponseUsesLockedStateAndRejectsAfterInspectionCompletion() throws Exception {
    inspection.complete();
    var responseValue = objectMapper.readTree("{\"value\":\"PASS\"}");

    assertThatThrownBy(
            () ->
                service.saveChecklistResponse(
                    inspectorId,
                    inspectionId,
                    checklistItemId,
                    new ChecklistResponseRequest(responseValue, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_STATE_CONFLICT"));

    verify(inspections).findForUpdateByIdAndAuthorUserId(inspectionId, inspectorId);
    verify(responses, never()).saveAndFlush(any());
  }

  @Test
  void checklistResponseAcquiresInspectionLockBeforeCheckingStatusAndSaving() throws Exception {
    when(templates.findDetailedById(templateId)).thenReturn(Optional.of(template));
    when(template.getItems()).thenReturn(List.of(item));
    when(item.getId()).thenReturn(checklistItemId);
    when(item.getResponseType()).thenReturn(ChecklistResponseType.PASS_FAIL);
    when(item.isRequired()).thenReturn(true);
    when(responses.findByInspectionIdAndChecklistItemId(inspectionId, checklistItemId))
        .thenReturn(Optional.empty());
    when(responses.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var responseValue = objectMapper.readTree("{\"value\":\"PASS\"}");

    service.saveChecklistResponse(
        inspectorId,
        inspectionId,
        checklistItemId,
        new ChecklistResponseRequest(responseValue, null));

    verify(inspections).findForUpdateByIdAndAuthorUserId(inspectionId, inspectorId);
    verify(inspections, never()).findById(inspectionId);
    verify(responses).saveAndFlush(any());
  }
}
