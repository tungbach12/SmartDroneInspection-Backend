package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.CaseMatch;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.GuidelineMatch;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.HistoricalCaseService.CaseRow;
import com.smartdroneinspection.inspections.service.HistoricalCaseService.GuidelineRow;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.CaseReference;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.GuidelineReference;
import com.smartdroneinspection.inspections.spi.HistoricalCaseAdvisorPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HistoricalCaseServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID ASSET_ID = UUID.randomUUID();

  private HistoricalCaseService service;
  private HistoricalCaseAdvisorPort advisorPort;
  private InspectionRepository inspections;
  private FindingRepository findings;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    advisorPort = mock(HistoricalCaseAdvisorPort.class);
    inspections = mock(InspectionRepository.class);
    findings = mock(FindingRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    service =
        new HistoricalCaseService(
            jdbcTemplate,
            mock(EvidenceService.class),
            inspections,
            findings,
            mock(FindingService.class),
            Optional.of(advisorPort));
  }

  @Test
  void pgVectorLiteralUsesLocaleIndependentDecimalsAndFullDimension() {
    float[] vector = new float[384];
    vector[0] = 0.5f;
    vector[1] = -1.25f;
    vector[383] = 1.0f;
    vector[10] = Float.NaN;

    String literal = HistoricalCaseService.toPgVectorLiteral(vector);

    assertThat(literal).startsWith("[0.500000,-1.250000,");
    assertThat(literal).endsWith(",1.000000]");
    assertThat(literal.split(",", -1)).hasSize(384);
    assertThat(literal).contains(",0,"); // non-finite values are normalized to zero
    assertThat(HistoricalCaseService.toPgVectorLiteral(null)).isEqualTo("[]");
    assertThat(HistoricalCaseService.toPgVectorLiteral(new float[0])).isEqualTo("[]");
  }

  @Test
  void hybridScoreFavoursCategoryDefectSeverityAndTenantMatches() {
    Set<String> queryTokens = HistoricalCaseService.tokens("Facade crack on the curtain wall HIGH");
    CaseRow best = row("Facade crack", "HIGH", "HIGH_RISE_OFFICE", ORGANIZATION_ID, ASSET_ID);
    CaseRow differentCategory =
        row("Facade crack", "HIGH", "RESIDENTIAL_TOWER", ORGANIZATION_ID, ASSET_ID);
    CaseRow differentSeverity =
        row("Facade crack", "LOW", "HIGH_RISE_OFFICE", ORGANIZATION_ID, ASSET_ID);
    CaseRow otherTenant =
        row("Facade crack", "HIGH", "HIGH_RISE_OFFICE", UUID.randomUUID(), UUID.randomUUID());

    double bestScore =
        HistoricalCaseService.hybridScore(
            best, queryTokens, "HIGH", "HIGH_RISE_OFFICE", ORGANIZATION_ID, ASSET_ID);

    assertThat(bestScore).isPositive();
    assertThat(
            HistoricalCaseService.hybridScore(
                differentCategory,
                queryTokens,
                "HIGH",
                "HIGH_RISE_OFFICE",
                ORGANIZATION_ID,
                ASSET_ID))
        .isLessThan(bestScore);
    assertThat(
            HistoricalCaseService.hybridScore(
                differentSeverity,
                queryTokens,
                "HIGH",
                "HIGH_RISE_OFFICE",
                ORGANIZATION_ID,
                ASSET_ID))
        .isLessThan(bestScore);
    assertThat(
            HistoricalCaseService.hybridScore(
                otherTenant, queryTokens, "HIGH", "HIGH_RISE_OFFICE", ORGANIZATION_ID, ASSET_ID))
        .isLessThan(bestScore);
    assertThat(
            HistoricalCaseService.hybridScore(
                best, queryTokens, null, "HIGH_RISE_OFFICE", ORGANIZATION_ID, ASSET_ID))
        .isLessThan(bestScore);
  }

  @Test
  void rankHybridEmitsClientFieldsWithNormalizedSimilarityAndScopeFlags() {
    CaseRow best = row("Facade crack", "HIGH", "HIGH_RISE_OFFICE", ORGANIZATION_ID, ASSET_ID);
    CaseRow middle = row("Water leakage", "MEDIUM", "HIGH_RISE_OFFICE", null, null);
    CaseRow weakest = row("Anchor corrosion", "LOW", "TELECOM_ROOFTOP_TOWER", null, null);

    List<CaseMatch> ranked =
        HistoricalCaseService.rankHybrid(
            List.of(weakest, middle, best),
            "Facade crack HIGH",
            "HIGH_RISE_OFFICE",
            ORGANIZATION_ID,
            ASSET_ID,
            2);

    assertThat(ranked).hasSize(2);
    assertThat(ranked.get(0).defectLabel()).isEqualTo("Facade crack");
    assertThat(ranked.get(0).assetCategory()).isEqualTo("HIGH_RISE_OFFICE");
    assertThat(ranked.get(0).outcome()).isEqualTo("PUBLISHED");
    assertThat(ranked.get(0).sameAsset()).isTrue();
    assertThat(ranked.get(0).sameOrganization()).isTrue();
    assertThat(ranked.get(1).sameAsset()).isFalse();
    assertThat(ranked.get(1).sameOrganization()).isFalse();
    assertThat(ranked)
        .allSatisfy(
            item -> {
              assertThat(item.similarity()).isBetween(0.0d, 1.0d);
              assertThat(item.technicalNotes()).isEqualTo("Observed during inspection");
            });
  }

  @Test
  void guidelineHybridRankingNormalizesSimilarityAndPrefersCategoryPatternAndSeverity() {
    GuidelineRow best =
        guideline("TCVN 9343:2012", "HIGH_RISE_OFFICE", "Facade crack|Spalling", "HIGH");
    GuidelineRow other =
        guideline("ISO 12944-5", "INDUSTRIAL_FACTORY", "Roof sheet corrosion", "LOW");

    List<GuidelineMatch> ranked =
        HistoricalCaseService.rankGuidelinesHybrid(
            List.of(other, best), "Facade crack HIGH", "HIGH_RISE_OFFICE", 2);

    assertThat(ranked).hasSize(2);
    assertThat(ranked.get(0).standardCode()).isEqualTo("TCVN 9343:2012");
    assertThat(ranked.get(0).referenceUrl()).isEqualTo("https://example.test/standard");
    assertThat(ranked.get(0).similarity()).isBetween(0.0d, 1.0d);
    assertThat(ranked.get(0).similarity()).isGreaterThan(ranked.get(1).similarity());
  }

  @Test
  void similarityNormalizationClampsToTheClientPercentageRange() {
    assertThat(HistoricalCaseService.normalizeSimilarity(0.85d)).isEqualTo(0.85d);
    assertThat(HistoricalCaseService.normalizeSimilarity(3.0d)).isEqualTo(1.0d);
    assertThat(HistoricalCaseService.normalizeSimilarity(-1.0d)).isEqualTo(0.0d);
    assertThat(HistoricalCaseService.normalizeSimilarity(Double.NaN)).isEqualTo(0.0d);
    assertThat(HistoricalCaseService.normalizeSimilarity(null)).isEqualTo(0.0d);
    assertThat(HistoricalCaseService.normalizeHybridScore(14.0d, 14.0d)).isEqualTo(1.0d);
    assertThat(HistoricalCaseService.normalizeHybridScore(7.0d, 14.0d)).isEqualTo(0.5d);
    assertThat(HistoricalCaseService.normalizeHybridScore(1.0d, 0.0d)).isEqualTo(0.0d);
    assertThat(HistoricalCaseService.severityProximity("HIGH", "HIGH")).isEqualTo(2.0d);
    assertThat(HistoricalCaseService.severityProximity("HIGH", "MEDIUM")).isEqualTo(1.0d);
    assertThat(HistoricalCaseService.severityProximity("HIGH", "LOW")).isEqualTo(0.5d);
    assertThat(HistoricalCaseService.severityProximity(null, "LOW")).isEqualTo(0.0d);
  }

  @Test
  void tokenAndSeverityExtractionIgnoreShortNoiseWords() {
    assertThat(HistoricalCaseService.tokens("Facade-crack FACADE! a 1"))
        .containsExactlyInAnyOrder("facade", "crack");
    assertThat(HistoricalCaseService.tokens(null)).isEmpty();
    assertThat(HistoricalCaseService.severityIn("Visible CRITICAL cracking")).isEqualTo("CRITICAL");
    assertThat(HistoricalCaseService.severityIn("no severity here")).isNull();
    assertThat(HistoricalCaseService.severityIn(null)).isNull();
  }

  @Test
  void embeddingFailuresAndWrongDimensionsDegradeCleanly() {
    assertThat(service.embed("Facade crack HIGH")).isEmpty(); // unstubbed mock returns empty

    when(advisorPort.embed(anyString())).thenThrow(new IllegalStateException("timeout"));
    assertThat(service.embed("Facade crack HIGH")).isEmpty();

    doReturn(Optional.of(new float[3])).when(advisorPort).embed(anyString());
    assertThat(service.embed("Facade crack HIGH")).isEmpty();

    float[] valid = new float[384];
    doReturn(Optional.of(valid)).when(advisorPort).embed(anyString());
    assertThat(service.embed("Facade crack HIGH")).contains(valid);
  }

  @Test
  void backfillMissingEmbeddingsPopulatesBothGuidelinesAndHistoricalCases() {
    UUID chunkId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    float[] valid = new float[384];
    valid[0] = 0.5f;
    doReturn(Optional.of(valid)).when(advisorPort).embed(anyString());
    when(jdbcTemplate.query(
            contains("FROM inspection_knowledge_chunks"),
            any(org.springframework.jdbc.core.RowMapper.class)))
        .thenAnswer(
            invocation -> {
              var rs = mock(java.sql.ResultSet.class);
              when(rs.getObject("id", UUID.class)).thenReturn(chunkId);
              when(rs.getString("standard_code")).thenReturn("TCVN 9343:2012");
              when(rs.getString("title")).thenReturn("Concrete crack");
              when(rs.getString("defect_patterns")).thenReturn("Crack");
              when(rs.getString("severity")).thenReturn("HIGH");
              when(rs.getString("guidance")).thenReturn("Inspect width");
              org.springframework.jdbc.core.RowMapper<?> mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(rs, 0));
            });
    when(jdbcTemplate.query(
            contains("FROM historical_finding_cases"),
            any(org.springframework.jdbc.core.RowMapper.class)))
        .thenAnswer(
            invocation -> {
              var rs = mock(java.sql.ResultSet.class);
              when(rs.getObject("id", UUID.class)).thenReturn(caseId);
              when(rs.getString("defect_label")).thenReturn("Facade crack");
              when(rs.getString("severity")).thenReturn("HIGH");
              when(rs.getString("technical_notes")).thenReturn("Hairline crack");
              org.springframework.jdbc.core.RowMapper<?> mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(rs, 0));
            });

    int missing = service.backfillMissingEmbeddings();

    assertThat(missing).isZero();
    verify(jdbcTemplate)
        .update(
            contains("UPDATE inspection_knowledge_chunks SET embedding = CAST(? AS vector)"),
            anyString(),
            eq(chunkId));
    verify(jdbcTemplate)
        .update(
            contains("UPDATE historical_finding_cases SET embedding = CAST(? AS vector)"),
            anyString(),
            eq(caseId));
  }

  @Test
  void absentInspectionIsANoOpForIndexingAndReferences() {
    when(inspections.findById(any())).thenReturn(Optional.empty());

    service.indexPublishedFindings(UUID.randomUUID());

    assertThat(service.findKnowledgeReferences(UUID.randomUUID())).isEmpty();
    assertThat(service.findKnowledgeReferences(null)).isEmpty();
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void indexPublishedFindingsUpsertsOfficialFindingsAndPropagatesReinspectionOutcomes() {
    UUID inspectionId = UUID.randomUUID();
    Inspection inspection = mock(Inspection.class);
    when(inspection.getOwnerCustomerId()).thenReturn(ORGANIZATION_ID);
    when(inspection.getAssetId()).thenReturn(ASSET_ID);
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    Finding official =
        new Finding(
            inspectionId,
            UUID.randomUUID(),
            "F-001",
            "Facade crack",
            FindingSeverity.HIGH,
            "Hairline crack near north mullion.",
            null,
            null,
            null);
    official.confirmByInspector();
    Finding pending =
        new Finding(
            inspectionId,
            UUID.randomUUID(),
            "F-002",
            "Surface stain",
            FindingSeverity.LOW,
            "Pending candidate",
            null,
            null,
            null);
    when(findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId))
        .thenReturn(List.of(official, pending));

    service.indexPublishedFindings(inspectionId);

    verify(jdbcTemplate)
        .update(
            contains("ON CONFLICT (source_finding_id) DO UPDATE"),
            any(),
            eq(inspectionId),
            eq(ORGANIZATION_ID),
            eq(ASSET_ID),
            any(),
            any(),
            eq("Facade crack"),
            eq("HIGH"),
            eq("Hairline crack near north mullion."),
            any(),
            any());
    verify(jdbcTemplate)
        .update(contains("FROM reinspection_defect_verifications"), eq(inspectionId));
  }

  @Test
  void deterministicNoteIsCompactGroundedAndCitesTheTopReferences() {
    String note =
        HistoricalCaseService.synthesizeDeterministicNote(
            draftCommand("en", "Facade crack", "HIGH", "Crack observed near the mid-span."));

    assertThat(note).contains("High-severity Facade crack is observed within the marked region.");
    assertThat(note).contains("Crack observed near the mid-span.");
    assertThat(note).contains("Per TCVN 9343:2012, classify visible cracks by width");
    assertThat(note).doesNotContain("Inspector's current note:");
    assertThat(note).doesNotContain("recorded published");
    assertThat(note).doesNotContain(ORGANIZATION_ID.toString());
    assertThat(note.chars().filter(character -> character == '.').count()).isBetween(2L, 3L);
  }

  @Test
  void deterministicNoteSupportsVietnameseAndPreservesAnExistingDraft() {
    String note =
        HistoricalCaseService.synthesizeDeterministicNote(
            draftCommand("vi", "Facade crack", "HIGH", "Vết nứt đã thấy tại vị trí giữa nhịp."));

    assertThat(note)
        .contains("Ghi nhận Facade crack ở mức độ cao trong vùng đã đánh dấu.")
        .contains("Vết nứt đã thấy tại vị trí giữa nhịp.")
        .contains("Theo tiêu chuẩn TCVN 9343:2012, cần")
        .doesNotContain("Ghi chú hiện tại của kiểm viên:")
        .doesNotContain("published");
  }

  @Test
  void deterministicNoteSkipsDuplicateLabelAndDoesNotTruncateWords() {
    DraftFindingNoteCommand command =
        new DraftFindingNoteCommand(
            "abscission",
            "MEDIUM",
            "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}",
            "abscission",
            "en",
            "HIGH_RISE_OFFICE",
            "Tower A",
            null,
            null,
            null,
            List.of(new DraftFindingNoteCommand.CaseReference("abscission", "notes", "PUBLISHED")),
            List.of(
                new DraftFindingNoteCommand.GuidelineReference(
                    "EN 1504-9",
                    "Concrete repair",
                    "Define the repair objective, document the as-found condition, and plan a follow-up inspection.")));

    String note = HistoricalCaseService.synthesizeDeterministicNote(command);

    assertThat(note)
        .contains("Medium-severity abscission is observed within the marked region.")
        .contains("plan a follow-up inspection.")
        .doesNotContain("Inspector's current note:")
        .doesNotContain("follow-up inspect;")
        .doesNotContain("recorded published");
  }

  @Test
  void deterministicNoteWithoutReferencesOrExistingNoteStaysTwoSentences() {
    DraftFindingNoteCommand command =
        new DraftFindingNoteCommand(
            "Coating blistering",
            "LOW",
            null,
            null,
            "en",
            "BRIDGE",
            "Main span",
            null,
            null,
            null,
            List.of(),
            List.of());

    String note = HistoricalCaseService.synthesizeDeterministicNote(command);

    assertThat(note)
        .contains("Low-severity Coating blistering is observed.")
        .contains("Document the affected area and verify substrate condition");
    assertThat(note.chars().filter(character -> character == '.').count()).isEqualTo(2L);
    assertThat(HistoricalCaseService.normalizeLocale(null)).isEqualTo("en");
    assertThat(HistoricalCaseService.normalizeLocale("vi-VN")).isEqualTo("vi");
    assertThat(HistoricalCaseService.normalizeLocale("de")).isEqualTo("en");
  }

  private static DraftFindingNoteCommand draftCommand(
      String locale, String defect, String severity, String existingNote) {
    return new DraftFindingNoteCommand(
        defect,
        severity,
        "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}",
        existingNote,
        locale,
        "HIGH_RISE_OFFICE",
        "Main tower",
        null,
        null,
        null,
        List.of(new CaseReference(defect, "Observed during a prior field session", "PUBLISHED")),
        List.of(
            new GuidelineReference(
                "TCVN 9343:2012",
                "Concrete surface crack assessment",
                "Classify visible cracks by width. Record their exact location.")));
  }

  private static CaseRow row(
      String defectLabel, String severity, String categoryCode, UUID organizationId, UUID assetId) {
    return new CaseRow(
        UUID.randomUUID(),
        assetId,
        organizationId,
        categoryCode,
        "Test asset",
        defectLabel,
        severity,
        "Observed during inspection",
        "PUBLISHED",
        false,
        Instant.now(),
        null);
  }

  private static GuidelineRow guideline(
      String standardCode, String categoryCode, String defectPatterns, String severity) {
    return new GuidelineRow(
        UUID.randomUUID(),
        standardCode,
        "Concrete surface crack assessment",
        severity,
        "Classify visible cracks by width",
        "https://example.test/standard",
        categoryCode,
        defectPatterns,
        null);
  }
}
