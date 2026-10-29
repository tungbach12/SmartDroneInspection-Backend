package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.response.InspectionReportResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
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
  @DisplayName("MANAGER can review and approve a submitted report version")
  void managerCanReviewReport() {
    UserAccess.ActiveUser manager =
        new UserAccess.ActiveUser(
            managerId,
            Set.of(Roles.MANAGER),
            null,
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

    InspectionReport version = new InspectionReport(inspectionId, 1, "snap");
    ReflectionTestUtils.setField(version, "id", versionId);
    ReflectionTestUtils.setField(version, "status", ReportStatus.SUBMITTED);

    when(userAccess.findActiveUser(managerId)).thenReturn(Optional.of(manager));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(versions.findById(versionId)).thenReturn(Optional.of(version));
    when(evidenceService.requireInspectionInScope(managerId, inspectionId)).thenReturn(inspection);
    when(versions.saveAndFlush(version)).thenReturn(version);

    InspectionReportResponse response =
        reportService.reviewVersion(
            managerId, inspectionId, versionId, true, "Approved for publish");

    assertThat(response.status()).isEqualTo(ReportStatus.APPROVED);
    verify(versions).saveAndFlush(version);
  }

  @Test
  @DisplayName("Report author cannot review their own report (separation of duties)")
  void authorCannotReviewOwnReport() {
    UserAccess.ActiveUser authorAsReviewer =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.MANAGER, Roles.INSPECTOR),
            null,
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
                    inspectorId, inspectionId, versionId, true, "Self review"))
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
            orgId,
            "CUSTOMER_ORGANIZATION",
            "Customer Client",
            "client@asset.test",
            null,
            null,
            null,
            null);

    InspectionReport draft = new InspectionReport(inspectionId, 1, "draft-snap");
    ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(draft, "status", ReportStatus.DRAFT);

    InspectionReport published = new InspectionReport(inspectionId, 2, "pub-snap");
    ReflectionTestUtils.setField(published, "id", versionId);
    ReflectionTestUtils.setField(published, "status", ReportStatus.PUBLISHED);

    when(userAccess.findActiveUser(customerId)).thenReturn(Optional.of(customer));
    when(versions.findByInspectionIdOrderByVersionNoDesc(inspectionId))
        .thenReturn(List.of(published, draft));

    List<InspectionReportResponse> result = reportService.listVersions(customerId, inspectionId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).status()).isEqualTo(ReportStatus.PUBLISHED);
  }

  @Test
  @DisplayName("CUSTOMER receives FORBIDDEN when attempting to read an unreleased DRAFT version")
  void customerCannotReadDraftVersion() {
    UserAccess.ActiveUser customer =
        new UserAccess.ActiveUser(
            customerId,
            Set.of(Roles.CUSTOMER),
            orgId,
            "CUSTOMER_ORGANIZATION",
            "Customer Client",
            "client@asset.test",
            null,
            null,
            null,
            null);

    InspectionReport draft = new InspectionReport(inspectionId, 1, "draft-snap");
    ReflectionTestUtils.setField(draft, "id", versionId);
    ReflectionTestUtils.setField(draft, "status", ReportStatus.DRAFT);

    when(userAccess.findActiveUser(customerId)).thenReturn(Optional.of(customer));
    when(versions.findById(versionId)).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> reportService.getVersion(customerId, inspectionId, versionId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only published reports are available to customer");
  }
}
