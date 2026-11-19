package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.CaseMatch;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.GuidelineMatch;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.HistoricalCaseService.CaseRow;
import com.smartdroneinspection.inspections.service.HistoricalCaseService.GuidelineRow;
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
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    advisorPort = mock(HistoricalCaseAdvisorPort.class);
    inspections = mock(InspectionRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    service =
        new HistoricalCaseService(
            jdbcTemplate,
            mock(EvidenceService.class),
            inspections,
            mock(FindingRepository.class),
            Optional.of(advisorPort));
  }

  @Test
  void pgVectorLiteralUsesLocaleIndependentDecimalsAndFullDimension() {
    float[] vector = new float[1536];
    vector[0] = 0.5f;
    vector[1] = -1.25f;
    vector[1535] = 1.0f;
    vector[10] = Float.NaN;

    String literal = HistoricalCaseService.toPgVectorLiteral(vector);

    assertThat(literal).startsWith("[0.500000,-1.250000,");
    assertThat(literal).endsWith(",1.000000]");
    assertThat(literal.split(",", -1)).hasSize(1536);
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

    float[] valid = new float[1536];
    doReturn(Optional.of(valid)).when(advisorPort).embed(anyString());
    assertThat(service.embed("Facade crack HIGH")).contains(valid);
  }

  @Test
  void absentInspectionIsANoOpForIndexingAndReferences() {
    when(inspections.findById(any())).thenReturn(Optional.empty());

    service.indexPublishedFindings(UUID.randomUUID());

    assertThat(service.findKnowledgeReferences(UUID.randomUUID())).isEmpty();
    assertThat(service.findKnowledgeReferences(null)).isEmpty();
    verifyNoInteractions(jdbcTemplate);
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
