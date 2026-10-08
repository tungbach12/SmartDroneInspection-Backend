package com.smartdroneinspection.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.InspectionAccess;
import com.smartdroneinspection.maintenance.api.dto.request.CreateMaintenanceTicketRequest;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketFindingRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.maintenance.service.MaintenanceTicketService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceTicketServiceTest {

  @Mock MaintenanceTicketRepository tickets;
  @Mock MaintenanceTicketFindingRepository ticketFindings;
  @Mock AssetRepository assets;
  @Mock InspectionAccess inspectionAccess;
  @Mock UserAccess users;

  MaintenanceTicketService service;

  final UUID clientId = UUID.randomUUID();
  final UUID orgId = UUID.randomUUID();
  final UUID otherOrgId = UUID.randomUUID();
  final UUID assetId = UUID.randomUUID();
  final UUID reportVersionId = UUID.randomUUID();
  final UUID findingId1 = UUID.randomUUID();
  final UUID findingId2 = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new MaintenanceTicketService(tickets, ticketFindings, assets, inspectionAccess, users);
  }

  @Test
  void createTicketFailsIfFindingsListIsEmpty() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));

    var req =
        new CreateMaintenanceTicketRequest(
            assetId,
            reportVersionId,
            List.of(),
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair crack",
            null);

    assertThatThrownBy(() -> service.createTicket(clientId, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).code()).isEqualTo("MAINTENANCE_FINDINGS_EMPTY"));
  }

  @Test
  void createTicketFailsIfAssetBelongsToOtherOrg() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.empty());

    var req =
        new CreateMaintenanceTicketRequest(
            assetId,
            reportVersionId,
            List.of(findingId1),
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair crack",
            null);

    assertThatThrownBy(() -> service.createTicket(clientId, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).code()).isEqualTo("REQUEST_SCOPE_DENIED"));
  }

  @Test
  void createTicketFailsIfReportVersionIsNotAccepted() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));
    Asset asset = org.mockito.Mockito.mock(Asset.class);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));

    when(inspectionAccess.findAcceptedReportVersion(reportVersionId)).thenReturn(Optional.empty());

    var req =
        new CreateMaintenanceTicketRequest(
            assetId,
            reportVersionId,
            List.of(findingId1),
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair crack",
            null);

    assertThatThrownBy(() -> service.createTicket(clientId, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).code()).isEqualTo("REPORT_NOT_ACCEPTED"));
  }

  @Test
  void createTicketSucceedsWithAcceptedReportAndPersistsFindings() {
    when(users.findActiveUser(clientId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(clientId, Set.of("CLIENT"), orgId)));
    Asset asset = org.mockito.Mockito.mock(Asset.class);
    when(assets.findByIdAndOrganizationId(assetId, orgId)).thenReturn(Optional.of(asset));

    var snapshot =
        new InspectionAccess.AcceptedReportSnapshot(
            reportVersionId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            assetId,
            Set.of(findingId1, findingId2));
    when(inspectionAccess.findAcceptedReportVersion(reportVersionId))
        .thenReturn(Optional.of(snapshot));

    when(tickets.save(any(MaintenanceTicket.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var req =
        new CreateMaintenanceTicketRequest(
            assetId,
            reportVersionId,
            List.of(findingId1, findingId2),
            MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair crack",
            null);

    service.createTicket(clientId, req);

    verify(tickets).save(any(MaintenanceTicket.class));
    verify(ticketFindings).saveAll(any());
  }
}
