package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChatThreadResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionMessage;
import com.smartdroneinspection.inspections.repository.InspectionMessageAttachmentRepository;
import com.smartdroneinspection.inspections.repository.InspectionMessageRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionChatServiceTest {

  private InspectionRepository inspections;
  private InspectionMessageRepository messages;
  private InspectionMessageAttachmentRepository attachments;
  private AssetRepository assets;
  private UserAccess users;
  private InspectionChatService service;

  private UUID customerId;
  private UUID inspectorId;
  private UUID managerId;
  private UUID inspectionId;
  private Inspection inspection;

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    messages = mock(InspectionMessageRepository.class);
    attachments = mock(InspectionMessageAttachmentRepository.class);
    assets = mock(AssetRepository.class);
    users = mock(UserAccess.class);
    service =
        new InspectionChatService(
            inspections, messages, attachments, assets, users, Optional.empty());

    customerId = UUID.randomUUID();
    inspectorId = UUID.randomUUID();
    managerId = UUID.randomUUID();
    inspectionId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();

    inspection =
        new Inspection(
            customerId,
            assetId,
            inspectorId,
            UUID.randomUUID(),
            Instant.now().plus(3, ChronoUnit.DAYS),
            Instant.now().plus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    Asset asset = mock(Asset.class);
    when(asset.getName()).thenReturn("Solar Farm Array A");
    when(assets.findById(assetId)).thenReturn(Optional.of(asset));

    when(users.findActiveUser(inspectorId))
        .thenReturn(Optional.of(user(inspectorId, Roles.INSPECTOR, "Le Van Inspector")));
    when(users.findUser(inspectorId))
        .thenReturn(Optional.of(user(inspectorId, Roles.INSPECTOR, "Le Van Inspector")));
    when(users.findActiveUser(customerId))
        .thenReturn(Optional.of(user(customerId, Roles.CUSTOMER, "Skyview Customer")));
    when(users.findUser(customerId))
        .thenReturn(Optional.of(user(customerId, Roles.CUSTOMER, "Skyview Customer")));
    when(users.findActiveUser(managerId))
        .thenReturn(Optional.of(user(managerId, Roles.MANAGER, "Tran Manager")));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(inspections.findWithLockById(inspectionId)).thenReturn(Optional.of(inspection));
  }

  private static ActiveUser user(UUID id, String role, String fullName) {
    return new ActiveUser(
        id,
        Set.of(role),
        fullName,
        role.toLowerCase() + "@test.local",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void assignedInspectorAndOwningCustomerCanExchangeMessages() {
    when(messages.saveAndFlush(any(InspectionMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    when(messages.findByInspectionIdOrderByCreatedAtAscIdAsc(inspectionId))
        .thenReturn(
            List.of(
                new InspectionMessage(
                    inspectionId, inspectorId, "Confirming gate access for tomorrow", false)));

    InspectionChatThreadResponse posted =
        service.postMessage(
            inspectorId, inspectionId, "Confirming gate access for tomorrow", List.of());

    assertThat(posted.counterpartName()).isEqualTo("Skyview Customer");
    assertThat(posted.messages()).hasSize(1);
    assertThat(posted.messages().getFirst().body())
        .isEqualTo("Confirming gate access for tomorrow");

    InspectionChatThreadResponse customerView = service.getThread(customerId, inspectionId);
    assertThat(customerView.counterpartName()).isEqualTo("Le Van Inspector");
    assertThat(customerView.messages()).hasSize(1);
  }

  @Test
  void managerAndUnassignedInspectorAreForbiddenFromInspectionChat() {
    UUID otherInspectorId = UUID.randomUUID();
    when(users.findActiveUser(otherInspectorId))
        .thenReturn(Optional.of(user(otherInspectorId, Roles.INSPECTOR, "Other Inspector")));

    assertThatThrownBy(() -> service.listThreads(managerId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThatThrownBy(() -> service.getThread(otherInspectorId, inspectionId))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
