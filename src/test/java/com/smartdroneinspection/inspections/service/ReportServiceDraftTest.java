package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ReportServiceDraftTest {

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID INSPECTION_ID = UUID.randomUUID();
  private static final UUID VERSION_ID = UUID.randomUUID();

  private InspectionReportRepository versions;
  private InspectionRepository inspections;
  private EvidenceRepository evidence;
  private EvidenceService evidenceService;
  private Inspection inspection;
  private ReportService service;

  @BeforeEach
  void setUp() {
    versions = mock(InspectionReportRepository.class);
    inspections = mock(InspectionRepository.class);
    evidence = mock(EvidenceRepository.class);
    evidenceService = mock(EvidenceService.class);
    service =
        new ReportService(
            inspections,
            mock(ReportContextAccess.class),
            mock(JdbcTemplate.class),
            evidence,
            mock(FindingRepository.class),
            mock(FieldSessionRepository.class),
            versions,
            evidenceService,
            Optional.empty(),
            mock(ReportRenderPort.class),
            Optional.of(mock(EvidenceObjectStore.class)),
            mock(UserAccess.class),
            mock(ApplicationEventPublisher.class),
            mock(tools.jackson.databind.ObjectMapper.class));

    inspection =
        new Inspection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ACTOR_ID,
            UUID.randomUUID(),
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.FIELD_COMPLETED);
    inspection.confirmFindings();

    when(evidenceService.requireAssignedInspection(ACTOR_ID, INSPECTION_ID)).thenReturn(inspection);
    when(evidence.existsByInspectionIdAndIsDeletedFalse(INSPECTION_ID)).thenReturn(true);
    when(inspections.findById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
    when(inspections.saveAndFlush(any(Inspection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(versions.saveAndFlush(any(InspectionReport.class)))
        .thenAnswer(
            invocation -> {
              InspectionReport saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", VERSION_ID);
              return saved;
            });
  }

  @Test
  void generatesDraftWithProvidedSummaryAndMarksInspectionUnderReview() {
    when(versions.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.empty());

    var response =
        service.generateDraft(
            ACTOR_ID, INSPECTION_ID, Locale.ENGLISH, "Prepared executive summary");

    assertThat(response.summary()).isEqualTo("Prepared executive summary");
    assertThat(response.status()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(response.authorUserId()).isEqualTo(ACTOR_ID);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.UNDER_REVIEW);
    org.mockito.Mockito.verify(versions).saveAndFlush(any(InspectionReport.class));
    org.mockito.Mockito.verify(inspections).saveAndFlush(inspection);
  }

  @Test
  void returnsExistingSubmittedVersionInsteadOfCreatingAnotherDraft() {
    InspectionReport submitted = new InspectionReport(INSPECTION_ID, 1, "Existing summary");
    ReflectionTestUtils.setField(submitted, "id", VERSION_ID);
    when(versions.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.of(submitted));

    var response = service.generateDraft(ACTOR_ID, INSPECTION_ID);

    assertThat(response.summary()).isEqualTo("Existing summary");
    org.mockito.Mockito.verify(versions, org.mockito.Mockito.never())
        .saveAndFlush(any(InspectionReport.class));
  }

  @Test
  void submitsReturnedVersionAfterReVerification() {
    InspectionReport returned = new InspectionReport(INSPECTION_ID, 1, "Returned summary");
    ReflectionTestUtils.setField(returned, "id", VERSION_ID);
    ReflectionTestUtils.setField(returned, "status", ReportStatus.RETURNED);
    when(versions.findById(VERSION_ID)).thenReturn(Optional.of(returned));

    var response = service.submitVersion(ACTOR_ID, INSPECTION_ID, VERSION_ID);

    assertThat(response.status()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(returned.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.UNDER_REVIEW);
    org.mockito.Mockito.verify(versions).saveAndFlush(returned);
  }

  @Test
  void rejectsDraftWhenFindingsAreNotConfirmed() {
    ReflectionTestUtils.setField(inspection, "findingsConfirmedAt", null);
    when(versions.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.empty());

    assertThatThrownByDraft();
  }

  private void assertThatThrownByDraft() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.generateDraft(ACTOR_ID, INSPECTION_ID, Locale.ENGLISH, "summary"))
        .isInstanceOfSatisfying(
            com.smartdroneinspection.shared.exception.BusinessException.class,
            exception -> assertThat(exception.code()).isEqualTo("FINDINGS_NOT_CONFIRMED"));
  }
}
