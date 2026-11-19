package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.response.InspectionReportResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.events.ReportReturnedEvent;
import com.smartdroneinspection.inspections.events.ReportSubmittedEvent;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceReviewerScopeTest {

  @Mock private InspectionReportRepository versions;
  @Mock private InspectionRepository inspections;
  @Mock private FindingRepository findings;
  @Mock private EvidenceRepository evidence;
  @Mock private FieldSessionRepository fieldSessions;
  @Mock private EvidenceService evidenceService;
  @Mock private ReportDraftPort draftPort;
  @Mock private ReportRenderPort renderPort;
  @Mock private UserAccess userAccess;
  @Mock private ApplicationEventPublisher events;
  @Mock private EvidenceObjectStore objectStore;

  @Mock
  private com.smartdroneinspection.assets.reportcontext.ReportContextAccess reportContextAccess;

  @Mock private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  @Mock private tools.jackson.databind.ObjectMapper objectMapper;

  private ReportService reportService;

  private final UUID inspectionId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    reportService =
        new ReportService(
            inspections,
            reportContextAccess,
            jdbcTemplate,
            evidence,
            findings,
            fieldSessions,
            versions,
            evidenceService,
            Optional.of(draftPort),
            renderPort,
            Optional.of(objectStore),
            userAccess,
            events,
            objectMapper);
  }

  @Test
  @DisplayName("MANAGER can review and publish a submitted report version")
  void managerCanPublishReport() throws java.io.IOException {
    UserAccess.ActiveUser manager =
        new UserAccess.ActiveUser(
            managerId,
            Set.of(Roles.MANAGER),
            "PLATFORM",
            "Platform Manager",
            "manager@platform.test",
            null,
            null,
            null,
            null);

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.UNDER_REVIEW);
    ReflectionTestUtils.setField(inspection, "findingsConfirmedAt", java.time.Instant.now());

    InspectionReport version = new InspectionReport(inspectionId, 1, "snap");
    ReflectionTestUtils.setField(version, "id", versionId);
    ReflectionTestUtils.setField(version, "status", ReportStatus.SUBMITTED);

    when(userAccess.findActiveUser(managerId)).thenReturn(Optional.of(manager));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(versions.findById(versionId)).thenReturn(Optional.of(version));
    when(evidenceService.requireInspectionInScope(managerId, inspectionId)).thenReturn(inspection);
    when(versions.saveAndFlush(version)).thenReturn(version);
    when(findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId)).thenReturn(List.of());
    when(versions.findByInspectionIdAndStatus(inspectionId, ReportStatus.PUBLISHED))
        .thenReturn(List.of());

    reportService.reviewVersion(managerId, inspectionId, versionId, null, java.util.Locale.ENGLISH);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.PUBLISHED);
    assertThat(version.getReviewerUserId()).isEqualTo(managerId);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.COMPLETED);
    verify(versions).saveAndFlush(version);
    verifyNoInteractions(renderPort, objectStore);

    ArgumentCaptor<ReportPublishedEvent> eventCaptor =
        ArgumentCaptor.forClass(ReportPublishedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    ReportPublishedEvent event = eventCaptor.getValue();
    assertThat(event.reportVersionId()).isEqualTo(versionId);
    assertThat(event.inspectionId()).isEqualTo(inspectionId);
    assertThat(event.ownerCustomerId()).isEqualTo(orgId);
    assertThat(event.publishedByUserId()).isEqualTo(managerId);
  }

  @Test
  @DisplayName("Submitting a report publishes a REPORT_SUBMITTED event for reviewer notices")
  void submitVersionPublishesReportSubmittedEvent() {
    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.FIELD_COMPLETED);
    ReflectionTestUtils.setField(inspection, "findingsConfirmedAt", java.time.Instant.now());

    InspectionReport version = new InspectionReport(inspectionId, 2, "snap");
    ReflectionTestUtils.setField(version, "id", versionId);
    ReflectionTestUtils.setField(version, "status", ReportStatus.SUBMITTED);

    when(evidenceService.requireAssignedInspection(inspectorId, inspectionId))
        .thenReturn(inspection);
    when(versions.findById(versionId)).thenReturn(Optional.of(version));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(evidence.existsByInspectionIdAndIsDeletedFalse(inspectionId)).thenReturn(true);
    when(versions.saveAndFlush(version)).thenReturn(version);

    reportService.submitVersion(inspectorId, inspectionId, versionId);

    ArgumentCaptor<ReportSubmittedEvent> eventCaptor =
        ArgumentCaptor.forClass(ReportSubmittedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    ReportSubmittedEvent event = eventCaptor.getValue();
    assertThat(event.inspectionId()).isEqualTo(inspectionId);
    assertThat(event.reportVersionId()).isEqualTo(versionId);
    assertThat(event.versionNo()).isEqualTo(2);
    assertThat(event.submittedByUserId()).isEqualTo(inspectorId);
    assertThat(event.submittedAt()).isNotNull();
  }

  @Test
  @DisplayName("Returning a report publishes a REPORT_RETURNED event for the author")
  void reviewerReturnPublishesReportReturnedEvent() {
    UserAccess.ActiveUser manager =
        new UserAccess.ActiveUser(
            managerId,
            Set.of(Roles.MANAGER),
            "PLATFORM",
            "Platform Manager",
            "manager@platform.test",
            null,
            null,
            null,
            null);

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.UNDER_REVIEW);

    InspectionReport version = new InspectionReport(inspectionId, 1, "snap");
    ReflectionTestUtils.setField(version, "id", versionId);
    ReflectionTestUtils.setField(version, "status", ReportStatus.SUBMITTED);

    when(userAccess.findActiveUser(managerId)).thenReturn(Optional.of(manager));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(versions.findById(versionId)).thenReturn(Optional.of(version));
    when(evidenceService.requireInspectionInScope(managerId, inspectionId)).thenReturn(inspection);
    when(versions.saveAndFlush(version)).thenReturn(version);

    reportService.reviewVersion(
        managerId,
        inspectionId,
        versionId,
        "Add a close-up of the crack",
        java.util.Locale.ENGLISH);

    ArgumentCaptor<ReportReturnedEvent> eventCaptor =
        ArgumentCaptor.forClass(ReportReturnedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    ReportReturnedEvent event = eventCaptor.getValue();
    assertThat(event.inspectionId()).isEqualTo(inspectionId);
    assertThat(event.reportVersionId()).isEqualTo(versionId);
    assertThat(event.inspectorId()).isEqualTo(inspectorId);
    assertThat(event.returnedByUserId()).isEqualTo(managerId);
    assertThat(event.reason()).isEqualTo("Add a close-up of the crack");
    assertThat(event.returnedAt()).isNotNull();
  }

  @Test
  @DisplayName("downloadArtefact renders PDF on demand if not yet cached in object store")
  void downloadArtefactRendersOnDemand() throws java.io.IOException {
    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    InspectionReport version = new InspectionReport(inspectionId, 1, "{}");
    ReflectionTestUtils.setField(version, "id", versionId);
    ReflectionTestUtils.setField(version, "status", ReportStatus.PUBLISHED);

    when(evidenceService.requireInspectionInScope(managerId, inspectionId)).thenReturn(inspection);
    when(versions.findById(versionId)).thenReturn(Optional.of(version));
    when(objectStore.open("reports/" + versionId + ".pdf"))
        .thenThrow(new java.io.FileNotFoundException());
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(renderPort.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[] {1, 2, 3});

    org.springframework.http.ResponseEntity<byte[]> response =
        reportService.downloadArtefact(managerId, inspectionId, versionId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isEqualTo(new byte[] {1, 2, 3});
    verify(renderPort).render(org.mockito.ArgumentMatchers.any());
    verify(objectStore)
        .put(
            org.mockito.ArgumentMatchers.eq("reports/" + versionId + ".pdf"),
            org.mockito.ArgumentMatchers.eq("application/pdf"),
            org.mockito.ArgumentMatchers.eq(3L),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Report author cannot review their own report (separation of duties)")
  void authorCannotReviewOwnReport() {
    UserAccess.ActiveUser authorAsReviewer =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.MANAGER, Roles.INSPECTOR),
            "PLATFORM",
            "Inspector Manager",
            "inspector@platform.test",
            null,
            null,
            null,
            null);

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(authorAsReviewer));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    assertThatThrownBy(
            () ->
                reportService.reviewVersion(
                    inspectorId, inspectionId, versionId, "Self review", java.util.Locale.ENGLISH))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("author cannot review their own report");
  }

  @Test
  @DisplayName("CUSTOMER sees only PUBLISHED versions in listVersions")
  void customerSeesOnlyPublishedVersions() {
    UserAccess.ActiveUser customer =
        new UserAccess.ActiveUser(
            customerId,
            Set.of(Roles.CUSTOMER),
            "CUSTOMER",
            "Customer Client",
            "client@asset.test",
            null,
            null,
            null,
            null);

    InspectionReport submitted = new InspectionReport(inspectionId, 1, "sub-snap");
    ReflectionTestUtils.setField(submitted, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(submitted, "status", ReportStatus.SUBMITTED);

    InspectionReport published = new InspectionReport(inspectionId, 2, "pub-snap");
    ReflectionTestUtils.setField(published, "id", versionId);
    ReflectionTestUtils.setField(published, "status", ReportStatus.PUBLISHED);

    when(userAccess.findActiveUser(customerId)).thenReturn(Optional.of(customer));
    when(versions.findByInspectionIdOrderByVersionNoDesc(inspectionId))
        .thenReturn(List.of(published, submitted));

    List<InspectionReportResponse> result = reportService.listVersions(customerId, inspectionId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).status()).isEqualTo(ReportStatus.PUBLISHED);
  }

  @Test
  @DisplayName(
      "CUSTOMER receives FORBIDDEN when attempting to read an unreleased SUBMITTED version")
  void customerCannotReadSubmittedVersion() {
    UserAccess.ActiveUser customer =
        new UserAccess.ActiveUser(
            customerId,
            Set.of(Roles.CUSTOMER),
            "CUSTOMER",
            "Customer Client",
            "client@asset.test",
            null,
            null,
            null,
            null);

    InspectionReport submitted = new InspectionReport(inspectionId, 1, "sub-snap");
    ReflectionTestUtils.setField(submitted, "id", versionId);
    ReflectionTestUtils.setField(submitted, "status", ReportStatus.SUBMITTED);

    when(userAccess.findActiveUser(customerId)).thenReturn(Optional.of(customer));
    when(versions.findById(versionId)).thenReturn(Optional.of(submitted));

    assertThatThrownBy(() -> reportService.getVersion(customerId, inspectionId, versionId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only published reports are available to customer");
  }

  @Test
  @DisplayName("MANAGER receives FORBIDDEN when requesting report clarification")
  void managerCannotRequestClarification() {
    Inspection inspection =
        new Inspection(customerId, UUID.randomUUID(), inspectorId, null, null, null);
    UserAccess.ActiveUser manager =
        new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), "Platform Manager");

    when(evidenceService.requireInspectionInScope(managerId, inspectionId)).thenReturn(inspection);
    when(userAccess.findActiveUser(managerId)).thenReturn(Optional.of(manager));

    assertThatThrownBy(
            () ->
                reportService.requestCustomerClarification(
                    managerId, inspectionId, versionId, "Clarify crack width"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("FORBIDDEN"));
    verifyNoInteractions(versions);
  }

  @Test
  @DisplayName(
      "a CUSTOMER from another organization receives FORBIDDEN when requesting clarification")
  void nonOwningCustomerCannotRequestClarification() {
    UUID outsiderId = UUID.randomUUID();
    Inspection inspection =
        new Inspection(customerId, UUID.randomUUID(), inspectorId, null, null, null);
    UserAccess.ActiveUser outsider =
        new UserAccess.ActiveUser(outsiderId, Set.of(Roles.CUSTOMER), "Other Customer");

    when(evidenceService.requireInspectionInScope(outsiderId, inspectionId)).thenReturn(inspection);
    when(userAccess.findActiveUser(outsiderId)).thenReturn(Optional.of(outsider));

    assertThatThrownBy(
            () ->
                reportService.requestCustomerClarification(
                    outsiderId, inspectionId, versionId, "Clarify crack width"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("FORBIDDEN"));
    verifyNoInteractions(versions);
  }
}
