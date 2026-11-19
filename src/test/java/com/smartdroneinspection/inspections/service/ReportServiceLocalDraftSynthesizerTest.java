package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DefectVerificationSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DraftContext;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.FindingSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.InspectionSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.SectionDraft;
import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ReportServiceLocalDraftSynthesizerTest {

  private ReportDraftPort synthesizer;

  @BeforeEach
  void setUp() {
    ReportService service =
        new ReportService(
            mock(InspectionRepository.class),
            mock(ReportContextAccess.class),
            mock(JdbcTemplate.class),
            mock(EvidenceRepository.class),
            mock(FindingRepository.class),
            mock(FieldSessionRepository.class),
            mock(InspectionReportRepository.class),
            mock(EvidenceService.class),
            Optional.empty(),
            mock(ReportRenderPort.class),
            Optional.of(mock(EvidenceObjectStore.class)),
            mock(UserAccess.class),
            mock(ApplicationEventPublisher.class),
            mock(tools.jackson.databind.ObjectMapper.class));
    synthesizer = ReflectionTestUtils.invokeMethod(service, "createLocalDraftSynthesizer");
  }

  @Test
  void reportsLocalSynthesizerAsModelName() {
    assertThat(synthesizer.modelName()).isEqualTo("local-synthesizer");
  }

  @Test
  void synthesizesEnglishNarrativeWithoutFindings() throws Exception {
    SectionDraft draft = synthesizer.draftSections(context(Locale.ENGLISH, List.of(), List.of()));

    assertThat(draft.executiveSummary())
        .contains("No structural defects were observed")
        .contains("assessed as Good");
    assertThat(draft.overallCondition()).isEqualTo("GOOD");
    assertThat(draft.conclusionsRecommendations())
        .contains("Routine periodic drone inspection schedule");
    assertThat(draft.omissionDisclosure()).contains("exterior surfaces");
    assertThat(draft.purposeScopeCriteria()).isNull();
    assertThat(draft.methodEquipmentConditions()).isNull();
  }

  @Test
  void synthesizesVietnameseNarrativeWithoutFindings() throws Exception {
    SectionDraft draft = synthesizer.draftSections(context(new Locale("vi"), List.of(), List.of()));

    assertThat(draft.executiveSummary()).contains("Không phát hiện khiếm khuyết").contains("Tốt");
    assertThat(draft.overallCondition()).isEqualTo("GOOD");
    assertThat(draft.conclusionsRecommendations()).contains("duy trì lịch trình bay");
  }

  @Test
  void derivesCriticalConditionAndUsesTechnicalNotesInEnglish() throws Exception {
    List<FindingSummary> findings =
        List.of(
            new FindingSummary("Crack", "CRITICAL", "Repair immediately"),
            new FindingSummary("Corrosion", "HIGH", " "),
            new FindingSummary("Discoloration", "MEDIUM", null));

    SectionDraft draft = synthesizer.draftSections(context(Locale.ENGLISH, findings, List.of()));

    assertThat(draft.overallCondition()).isEqualTo("CRITICAL");
    assertThat(draft.executiveSummary())
        .contains("3 confirmed defect(s)")
        .contains("Crack (CRITICAL)")
        .contains("Overall asset condition is assessed as CRITICAL");
    assertThat(draft.conclusionsRecommendations())
        .contains("Repair immediately")
        .contains("Corrosion")
        .contains("Discoloration");
  }

  @Test
  void derivesPoorAndFairConditionsForHighAndMediumSeverities() throws Exception {
    SectionDraft high =
        synthesizer.draftSections(
            context(
                Locale.ENGLISH, List.of(new FindingSummary("Corrosion", "HIGH", null)), List.of()));
    SectionDraft medium =
        synthesizer.draftSections(
            context(
                Locale.ENGLISH, List.of(new FindingSummary("Wear", "MEDIUM", null)), List.of()));

    assertThat(high.overallCondition()).isEqualTo("POOR");
    assertThat(medium.overallCondition()).isEqualTo("FAIR");
  }

  @Test
  void synthesizesVietnameseFindingsNarrative() throws Exception {
    SectionDraft draft =
        synthesizer.draftSections(
            context(
                new Locale("vi"),
                List.of(new FindingSummary("Nứt bê tông", "HIGH", "Sửa chữa khẩn cấp")),
                List.of()));

    assertThat(draft.executiveSummary())
        .contains("1 khiếm khuyết")
        .contains("Nứt bê tông (HIGH)")
        .contains("Kém");
    assertThat(draft.conclusionsRecommendations()).contains("Sửa chữa khẩn cấp");
  }

  @Test
  void summarisesFullyResolvedReinspectionInEnglish() throws Exception {
    SectionDraft draft =
        synthesizer.draftSections(
            context(
                Locale.ENGLISH,
                List.of(),
                List.of(
                    new DefectVerificationSummary("Crack", "HIGH", "RESOLVED", null),
                    new DefectVerificationSummary("Spalling", "MEDIUM", "resolved", null))));

    assertThat(draft.overallCondition()).isEqualTo("GOOD");
    assertThat(draft.executiveSummary())
        .contains("Confirmed 2 target defect(s) successfully resolved")
        .contains("meets re-inspection acceptance criteria");
    assertThat(draft.conclusionsRecommendations())
        .contains("verified as resolved")
        .contains("Suitable for customer acceptance");
    assertThat(draft.omissionDisclosure()).contains("focused on the specific target defect");
  }

  @Test
  void summarisesUnresolvedReinspectionWithFollowUpInVietnamese() throws Exception {
    SectionDraft draft =
        synthesizer.draftSections(
            context(
                new Locale("vi"),
                List.of(),
                List.of(
                    new DefectVerificationSummary("Nứt", "HIGH", "RESOLVED", null),
                    new DefectVerificationSummary("Thấm", "MEDIUM", "UNRESOLVED", null))));

    assertThat(draft.overallCondition()).isEqualTo("POOR");
    assertThat(draft.executiveSummary())
        .contains("1 khiếm khuyết đã khắc phục")
        .contains("1 khiếm khuyết chưa đạt")
        .contains("Kém");
    assertThat(draft.conclusionsRecommendations())
        .contains("cần tiếp tục")
        .contains("tái kiểm định bổ sung");
  }

  @Test
  void treatsInconclusiveReinspectionAsFairFollowUp() throws Exception {
    SectionDraft draft =
        synthesizer.draftSections(
            context(
                Locale.ENGLISH,
                List.of(),
                List.of(new DefectVerificationSummary("Crack", "HIGH", "INCONCLUSIVE", null))));

    assertThat(draft.overallCondition()).isEqualTo("FAIR");
    assertThat(draft.executiveSummary())
        .contains("Recorded 0 resolved defect(s)")
        .contains("requiring follow-up")
        .contains("assessed as FAIR");
  }

  @Test
  void nullLocaleAndNullVerificationsFallBackToEnglishFindingsNarrative() throws Exception {
    DraftContext context =
        new DraftContext(
            new InspectionSummary(List.of(), null, null, null, null),
            List.of(new FindingSummary("Crack", "LOW", null)),
            List.of(),
            List.of(),
            List.of(),
            null,
            null);

    SectionDraft draft = synthesizer.draftSections(context);

    assertThat(draft.executiveSummary())
        .contains("Drone aerial visual inspection completed with 1");
    assertThat(draft.overallCondition()).isEqualTo("GOOD");
  }

  private static DraftContext context(
      Locale locale, List<FindingSummary> findings, List<DefectVerificationSummary> verifications) {
    return new DraftContext(
        new InspectionSummary(List.of("Roof"), null, null, null, null),
        findings,
        List.of(),
        List.of(),
        List.of(),
        locale,
        verifications);
  }
}
