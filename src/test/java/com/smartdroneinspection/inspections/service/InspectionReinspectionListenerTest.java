package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReinspectionRequestedEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InspectionReinspectionListenerTest {

  private InspectionRepository inspections;
  private FindingRepository findings;
  private InspectionReinspectionListener listener;

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    findings = mock(FindingRepository.class);
    listener = new InspectionReinspectionListener(inspections, findings);
  }

  @Test
  void createsLinkedReinspectionOnEvent() {
    UUID workOrderId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID sourceFindingId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();

    Finding finding = mock(Finding.class);
    when(finding.getDefectLabel()).thenReturn("Concrete spalling");
    when(finding.getInspectionId()).thenReturn(UUID.randomUUID());
    when(findings.findById(sourceFindingId)).thenReturn(Optional.of(finding));

    Inspection sourceInspection = mock(Inspection.class);
    when(sourceInspection.getInspectorId()).thenReturn(inspectorId);
    when(sourceInspection.getDroneId()).thenReturn(droneId);
    when(sourceInspection.getScope()).thenReturn("Bridge pier inspection");
    when(inspections.findById(finding.getInspectionId())).thenReturn(Optional.of(sourceInspection));

    ReinspectionRequestedEvent event =
        new ReinspectionRequestedEvent(
            workOrderId,
            reportVersionId,
            orgId,
            assetId,
            UUID.randomUUID(),
            sourceFindingId,
            reviewerId,
            "Re-check ultrasonic density",
            Instant.now());

    listener.onReinspectionRequested(event);

    ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
    verify(inspections).saveAndFlush(captor.capture());

    Inspection saved = captor.getValue();
    assertThat(saved.getOrganizationId()).isEqualTo(orgId);
    assertThat(saved.getAssetId()).isEqualTo(assetId);
    assertThat(saved.getInspectorId()).isEqualTo(inspectorId);
    assertThat(saved.getObjective()).contains("Re-inspection for Work Order");
    assertThat(saved.getObjective()).contains("Concrete spalling");
    assertThat(saved.getObjective()).contains("Re-check ultrasonic density");
    assertThat(saved.getStatus()).isEqualTo(InspectionStatus.ASSIGNED);
  }
}
