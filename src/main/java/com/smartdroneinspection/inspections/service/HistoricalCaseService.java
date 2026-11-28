package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.DraftFindingNoteResponse;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.CaseMatch;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.GuidelineMatch;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeReferenceSummary;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.CaseReference;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.GuidelineReference;
import com.smartdroneinspection.inspections.spi.HistoricalCaseAdvisorPort;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-isolated historical case recommendations for MF3 findings and reports.
 *
 * <p>Private cases of another organization are never visible: every query is constrained to the
 * current inspection's owning customer or to explicitly anonymized benchmarks. A case produced by
 * the current inspection is excluded so an inspection cannot recommend itself. When both the query
 * and stored embeddings exist, ranking uses pgvector cosine similarity; otherwise a deterministic
 * hybrid score keeps the feature useful without an embedding provider. All emitted similarity
 * values are normalized to {@code [0.0, 1.0]} for the Web and Mobile match-percentage chips.
 */
@Service
public class HistoricalCaseService {

  public static final String MODE_PGVECTOR = "PGVECTOR_RAG";
  public static final String MODE_HYBRID = "HYBRID_MATCH";
  public static final String MODE_AI_VISION_RAG = "AI_VISION_RAG";
  public static final String MODE_DETERMINISTIC_RAG = "DETERMINISTIC_RAG";
  public static final String KIND_GUIDELINE = "GUIDELINE";
  public static final String KIND_CASE = "CASE";

  private static final int EMBEDDING_DIMENSIONS = 384;
  private static final int MAX_LIMIT = 10;
  private static final int TOP_MATCHES = 2;
  private static final int MAX_NOTE_IMAGE_BYTES = 4 * 1024 * 1024;
  private static final String ALL_CATEGORY = "ALL";

  /** category(3) + defect overlap(3×2) + same asset(2) + same org(1) + severity proximity(2). */
  private static final double HYBRID_CASE_MAX = 14.0d;

  /** category(3) + pattern overlap(3×2) + severity proximity(2). */
  private static final double HYBRID_GUIDELINE_MAX = 11.0d;

  private final JdbcTemplate jdbcTemplate;
  private final EvidenceService evidenceService;
  private final InspectionRepository inspections;
  private final FindingRepository findings;
  private final FindingService findingService;
  private final Optional<HistoricalCaseAdvisorPort> advisorPort;
  private final boolean knowledgeAiEnabled;

  public HistoricalCaseService(
      JdbcTemplate jdbcTemplate,
      EvidenceService evidenceService,
      InspectionRepository inspections,
      FindingRepository findings,
      FindingService findingService,
      Optional<HistoricalCaseAdvisorPort> advisorPort) {
    this(jdbcTemplate, evidenceService, inspections, findings, findingService, advisorPort, false);
  }

  @Autowired
  public HistoricalCaseService(
      JdbcTemplate jdbcTemplate,
      EvidenceService evidenceService,
      InspectionRepository inspections,
      FindingRepository findings,
      FindingService findingService,
      Optional<HistoricalCaseAdvisorPort> advisorPort,
      @Value("${app.knowledge-ai.enabled:false}") boolean knowledgeAiEnabled) {
    this.jdbcTemplate = jdbcTemplate;
    this.evidenceService = evidenceService;
    this.inspections = inspections;
    this.findings = findings;
    this.findingService = findingService;
    this.advisorPort = advisorPort;
    this.knowledgeAiEnabled = knowledgeAiEnabled;
  }

  /** Recommendations grounded in one confirmed finding of this inspection. */
  @Transactional(readOnly = true)
  public KnowledgeRecommendationResponse recommendForFinding(
      UUID actorId, UUID inspectionId, UUID findingId, int limit) {
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    Finding finding =
        findings
            .findByIdAndInspectionId(findingId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
    return recommend(inspection, describeFinding(finding), limit);
  }

  /** Recommendations grounded in all confirmed findings of this inspection. */
  @Transactional(readOnly = true)
  public KnowledgeRecommendationResponse recommendForInspection(
      UUID actorId, UUID inspectionId, int limit) {
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    String queryText =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(Finding::isOfficial)
            .map(HistoricalCaseService::describeFinding)
            .collect(Collectors.joining(" "));
    return recommend(inspection, queryText, limit);
  }

  /** Neutral payload used when the advisory capability is not wired at all. */
  public static KnowledgeRecommendationResponse emptyRecommendations() {
    return new KnowledgeRecommendationResponse(MODE_HYBRID, List.of(), List.of());
  }

  /** Neutral fallback for a controller built without the historical case service. */
  public static DraftFindingNoteResponse emptyDraftNote() {
    return new DraftFindingNoteResponse(
        "", MODE_DETERMINISTIC_RAG, MODE_HYBRID, List.of(), List.of());
  }

  /**
   * Drafts a concise, source-backed technical note for one finding.
   *
   * <p>The finding-edit scope guard is shared with {@link FindingService}: organization/assignment
   * scope and the locked-report guard are identical to editing a finding. Retrieval supplies the
   * two best cases and guidelines; the external vision/chat model may then synthesize a note from
   * the evidence image and that context, with a deterministic fallback when it is unavailable.
   */
  @Transactional(readOnly = true)
  public DraftFindingNoteResponse draftFindingNote(
      UUID actorId, UUID inspectionId, UUID findingId, String rawLocale, String existingNote) {
    Finding finding = findingService.requireEditableFinding(actorId, inspectionId, findingId);
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found"));
    KnowledgeRecommendationResponse matches =
        retrieve(inspection, describeFinding(finding), TOP_MATCHES, TOP_MATCHES);
    DraftFindingNoteCommand command =
        draftCommand(finding, inspection, matches, rawLocale, existingNote);

    Optional<String> aiNote = draftWithAdvisor(command);
    return new DraftFindingNoteResponse(
        aiNote.orElseGet(() -> synthesizeDeterministicNote(command)),
        aiNote.isPresent() ? MODE_AI_VISION_RAG : MODE_DETERMINISTIC_RAG,
        matches.retrievalMode(),
        matches.cases(),
        matches.guidelines());
  }

  private DraftFindingNoteCommand draftCommand(
      Finding finding,
      Inspection inspection,
      KnowledgeRecommendationResponse matches,
      String rawLocale,
      String existingNote) {
    String categoryCode = resolveCategoryCode(inspection.getAssetId());
    String assetLabel = resolveAssetLabel(inspection.getAssetId(), categoryCode);
    List<CaseReference> cases =
        matches.cases().stream()
            .limit(TOP_MATCHES)
            .map(
                item ->
                    new CaseReference(item.defectLabel(), item.technicalNotes(), item.outcome()))
            .toList();
    List<GuidelineReference> guidelines =
        matches.guidelines().stream()
            .limit(TOP_MATCHES)
            .map(item -> new GuidelineReference(item.standardCode(), item.title(), item.guidance()))
            .toList();
    Optional<EvidenceService.AdvisoryImage> image = readNoteImage(inspection.getId(), finding);

    return new DraftFindingNoteCommand(
        finding.getDefectLabel(),
        finding.getSeverity() == null ? null : finding.getSeverity().name(),
        finding.getBoundingBox(),
        existingNote,
        normalizeLocale(rawLocale),
        categoryCode,
        assetLabel,
        null,
        image.map(EvidenceService.AdvisoryImage::bytes).orElse(null),
        image.map(EvidenceService.AdvisoryImage::contentType).orElse(null),
        cases,
        guidelines);
  }

  private Optional<EvidenceService.AdvisoryImage> readNoteImage(
      UUID inspectionId, Finding finding) {
    if (finding.getEvidenceId() == null) {
      return Optional.empty();
    }
    try {
      return evidenceService.readAdvisoryImage(
          inspectionId, finding.getEvidenceId(), MAX_NOTE_IMAGE_BYTES);
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private Optional<String> draftWithAdvisor(DraftFindingNoteCommand command) {
    try {
      return advisorPort
          .flatMap(port -> port.draftFindingNote(command))
          .map(String::strip)
          .filter(note -> !note.isEmpty());
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  static String normalizeLocale(String locale) {
    String tag = locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
    return tag.equals("vi") || tag.startsWith("vi-") ? "vi" : "en";
  }

  /**
   * Deterministic fallback: a compact two-to-three sentence note built strictly from the finding
   * and the top retrieved references. It invents no measurements and quotes no identifiers.
   */
  static String synthesizeDeterministicNote(DraftFindingNoteCommand command) {
    boolean vietnamese = "vi".equals(normalizeLocale(command.locale()));
    String defect = cleanText(command.defectLabel(), "defect");
    String severity = cleanText(command.severity(), "").toUpperCase(Locale.ROOT);
    String region = regionPhrase(command.boundingBox(), command.checkpointTitle(), vietnamese);

    String observation;
    if (vietnamese) {
      observation =
          "Ghi nhận "
              + defect
              + (severity.isEmpty() ? "" : " ở mức độ " + severityLabelVi(severity))
              + region
              + ".";
    } else {
      observation =
          (severity.isEmpty()
                  ? capitalize(defect)
                  : capitalize(severity.toLowerCase(Locale.ROOT)) + "-severity " + defect)
              + " is observed"
              + region
              + ".";
    }

    String existing = meaningfulExistingNote(command.existingNote(), defect);
    StringBuilder note = new StringBuilder(observation);
    if (!existing.isEmpty()) {
      note.append(' ').append(asSentence(existing));
    }

    String reference = referenceSentence(command, vietnamese);
    if (!reference.isEmpty()) {
      note.append(' ').append(reference);
    } else if (existing.isEmpty()) {
      note.append(' ')
          .append(
              vietnamese
                  ? "Cần ghi nhận hiện trạng khu vực bị ảnh hưởng và đánh giá điều kiện bề mặt trước khi xử lý."
                  : "Document the affected area and verify substrate condition before planning remediation.");
    }
    return note.toString();
  }

  private static String referenceSentence(DraftFindingNoteCommand command, boolean vietnamese) {
    GuidelineReference guideline =
        command.guidelines() == null || command.guidelines().isEmpty()
            ? null
            : command.guidelines().getFirst();
    CaseReference matchedCase =
        command.cases() == null || command.cases().isEmpty() ? null : command.cases().getFirst();

    String standardCode = guideline == null ? "" : cleanText(guideline.standardCode(), "");
    String action = guideline == null ? "" : conciseAction(guideline.guidance());
    String rawOutcome = matchedCase == null ? "" : cleanText(matchedCase.outcome(), "");
    String outcome = isWorkflowStatusOnly(rawOutcome) ? "" : rawOutcome.toLowerCase(Locale.ROOT);

    if (!standardCode.isEmpty() && !action.isEmpty() && !outcome.isEmpty()) {
      return vietnamese
          ? "Theo tiêu chuẩn "
              + standardCode
              + ", cần "
              + action
              + "; một ca tương tự trước đây ghi nhận kết quả "
              + outcome
              + "."
          : "Per "
              + standardCode
              + ", "
              + action
              + "; a comparable case was recorded as "
              + outcome
              + ".";
    }
    if (!standardCode.isEmpty() && !action.isEmpty()) {
      return vietnamese
          ? "Theo tiêu chuẩn " + standardCode + ", cần " + action + "."
          : "Per " + standardCode + ", " + action + ".";
    }
    if (!outcome.isEmpty()) {
      String label =
          matchedCase == null
              ? ""
              : cleanText(matchedCase.defectLabel(), "defect").toLowerCase(Locale.ROOT);
      return vietnamese
          ? "Một ca tương tự về " + label + " trước đây ghi nhận kết quả " + outcome + "."
          : "A comparable " + label + " case was recorded as " + outcome + ".";
    }
    return "";
  }

  private static String regionPhrase(
      String boundingBox, String checkpointTitle, boolean vietnamese) {
    if (checkpointTitle != null && !checkpointTitle.isBlank()) {
      String checkpoint = checkpointTitle.strip();
      return vietnamese
          ? " tại điểm kiểm tra " + checkpoint
          : " at the " + checkpoint + " checkpoint";
    }
    if (boundingBox != null && !boundingBox.isBlank()) {
      return vietnamese ? " trong vùng đã đánh dấu" : " within the marked region";
    }
    return "";
  }

  private static String conciseAction(String guidance) {
    String text = cleanText(guidance, "");
    if (text.isEmpty()) {
      return "";
    }
    int maxLen = 180;
    int cut = text.length();
    for (String delimiter : List.of(". ", "; ")) {
      int index = text.indexOf(delimiter);
      if (index > 20 && index < cut) {
        cut = index;
      }
    }
    if (cut > maxLen) {
      int spaceCut = text.lastIndexOf(' ', maxLen);
      cut = spaceCut > 20 ? spaceCut : maxLen;
    }
    String action = text.substring(0, cut).strip();
    return action.replaceAll("[.;,:]+$", "").toLowerCase(Locale.ROOT);
  }

  private static String meaningfulExistingNote(String value, String defect) {
    String cleaned = cleanText(value, "");
    if (cleaned.isEmpty() || cleaned.equalsIgnoreCase(defect)) {
      return "";
    }
    String lower = cleaned.toLowerCase(Locale.ROOT);
    if (lower.equals("pending candidate")
        || lower.equals("confirmed by assigned inspector")
        || lower.equals("modified by assigned inspector")
        || lower.equals("corrected by assigned inspector")) {
      return "";
    }
    return cleaned;
  }

  private static boolean isWorkflowStatusOnly(String outcome) {
    if (outcome == null || outcome.isBlank()) {
      return true;
    }
    String lower = outcome.trim().toLowerCase(Locale.ROOT);
    return lower.equals("published")
        || lower.equals("approved")
        || lower.equals("submitted")
        || lower.equals("draft")
        || lower.equals("returned")
        || lower.equals("superseded");
  }

  private static String severityLabelVi(String severity) {
    return switch (severity) {
      case "CRITICAL" -> "nghiêm trọng";
      case "HIGH" -> "cao";
      case "MEDIUM" -> "trung bình";
      case "LOW" -> "thấp";
      default -> severity;
    };
  }

  private static String capitalize(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private static String asSentence(String text) {
    String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      return "";
    }
    char last = trimmed.charAt(trimmed.length() - 1);
    return Character.isLetterOrDigit(last) ? trimmed + "." : trimmed;
  }

  private static String cleanText(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String cleaned = value.strip().replaceAll("\\s+", " ");
    return cleaned.length() > 200 ? cleaned.substring(0, 200).stripTrailing() : cleaned;
  }

  private KnowledgeRecommendationResponse recommend(
      Inspection inspection, String queryText, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    return retrieve(inspection, queryText, safeLimit, safeLimit);
  }

  private KnowledgeRecommendationResponse retrieve(
      Inspection inspection, String queryText, int caseLimit, int guidelineLimit) {
    UUID organizationId = inspection.getOwnerCustomerId();
    UUID assetId = inspection.getAssetId();
    String categoryCode = resolveCategoryCode(assetId);
    Optional<float[]> embedding = embed(queryText);
    boolean vectorCases = embedding.isPresent() && hasIndexedEmbeddings();
    boolean vectorGuidelines = embedding.isPresent() && hasIndexedChunkEmbeddings();

    List<CaseMatch> cases =
        vectorCases
            ? queryPgVector(embedding.get(), organizationId, inspection.getId(), assetId, caseLimit)
            : rankHybrid(
                queryCandidates(organizationId, inspection.getId()),
                queryText,
                categoryCode,
                organizationId,
                assetId,
                caseLimit);

    List<GuidelineMatch> guidelines =
        vectorGuidelines
            ? queryGuidelinesPgVector(embedding.get(), guidelineLimit)
            : rankGuidelinesHybrid(
                queryGuidelineCandidates(), queryText, categoryCode, guidelineLimit);

    String retrievalMode = vectorCases || vectorGuidelines ? MODE_PGVECTOR : MODE_HYBRID;
    return new KnowledgeRecommendationResponse(retrievalMode, cases, guidelines);
  }

  private List<CaseMatch> queryPgVector(
      float[] queryEmbedding,
      UUID organizationId,
      UUID excludedInspectionId,
      UUID assetId,
      int limit) {
    String vectorLiteral = toPgVectorLiteral(queryEmbedding);
    return jdbcTemplate.query(
        """
        SELECT id, asset_id, organization_id, asset_category_code, asset_label, defect_label,
               severity, technical_notes, report_outcome, is_anonymized_benchmark, published_at,
               1 - (embedding <=> CAST(? AS vector)) AS similarity
        FROM historical_finding_cases
        WHERE (organization_id = CAST(? AS uuid) OR is_anonymized_benchmark = TRUE)
          AND (source_inspection_id IS NULL OR source_inspection_id <> CAST(? AS uuid))
          AND embedding IS NOT NULL
        ORDER BY embedding <=> CAST(? AS vector)
        LIMIT ?
        """,
        (rs, rowNum) ->
            toCaseMatch(
                mapCaseRow(rs, rowNum),
                organizationId,
                assetId,
                normalizeSimilarity(rs.getObject("similarity", Double.class))),
        vectorLiteral,
        organizationId,
        excludedInspectionId,
        vectorLiteral,
        limit);
  }

  private List<CaseRow> queryCandidates(UUID organizationId, UUID excludedInspectionId) {
    return jdbcTemplate.query(
        """
        SELECT id, asset_id, organization_id, asset_category_code, asset_label, defect_label,
               severity, technical_notes, report_outcome, is_anonymized_benchmark, published_at,
               NULL::double precision AS similarity
        FROM historical_finding_cases
        WHERE (organization_id = CAST(? AS uuid) OR is_anonymized_benchmark = TRUE)
          AND (source_inspection_id IS NULL OR source_inspection_id <> CAST(? AS uuid))
        """,
        this::mapCaseRow,
        organizationId,
        excludedInspectionId);
  }

  private List<GuidelineMatch> queryGuidelinesPgVector(float[] queryEmbedding, int limit) {
    String vectorLiteral = toPgVectorLiteral(queryEmbedding);
    return jdbcTemplate.query(
        """
        SELECT id, standard_code, title, severity, guidance, reference_url,
               asset_category_code, defect_patterns,
               1 - (embedding <=> CAST(? AS vector)) AS similarity
        FROM inspection_knowledge_chunks
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(? AS vector)
        LIMIT ?
        """,
        (rs, rowNum) ->
            toGuidelineMatch(
                mapGuidelineRow(rs, rowNum),
                normalizeSimilarity(rs.getObject("similarity", Double.class))),
        vectorLiteral,
        vectorLiteral,
        limit);
  }

  private List<GuidelineRow> queryGuidelineCandidates() {
    return jdbcTemplate.query(
        """
        SELECT id, standard_code, title, severity, guidance, reference_url,
               asset_category_code, defect_patterns, NULL::double precision AS similarity
        FROM inspection_knowledge_chunks
        """,
        this::mapGuidelineRow);
  }

  private boolean hasIndexedEmbeddings() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM historical_finding_cases WHERE embedding IS NOT NULL",
            Long.class);
    return count != null && count > 0;
  }

  private boolean hasIndexedChunkEmbeddings() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inspection_knowledge_chunks WHERE embedding IS NOT NULL",
            Long.class);
    return count != null && count > 0;
  }

  /** Indexes the confirmed findings of a newly published report inside the caller's transaction. */
  @Transactional
  public void indexPublishedFindings(UUID inspectionId) {
    Inspection inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection == null) {
      return;
    }
    String categoryCode = resolveCategoryCode(inspection.getAssetId());
    String assetLabel = resolveAssetLabel(inspection.getAssetId(), categoryCode);
    Instant publishedAt = Instant.now();
    for (Finding finding :
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(Finding::isOfficial)
            .toList()) {
      String queryText = describeFinding(finding);
      String vector = embed(queryText).map(HistoricalCaseService::toPgVectorLiteral).orElse(null);
      jdbcTemplate.update(
          """
          INSERT INTO historical_finding_cases
            (source_finding_id, source_inspection_id, organization_id, asset_id,
             asset_category_code, asset_label, defect_label, severity, technical_notes,
             report_outcome, is_anonymized_benchmark, embedding, published_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', FALSE, CAST(? AS vector), ?)
          ON CONFLICT (source_finding_id) DO UPDATE
          SET defect_label = EXCLUDED.defect_label,
              severity = EXCLUDED.severity,
              technical_notes = EXCLUDED.technical_notes,
              report_outcome = EXCLUDED.report_outcome,
              embedding = COALESCE(EXCLUDED.embedding, historical_finding_cases.embedding)
          """,
          finding.getId(),
          inspectionId,
          inspection.getOwnerCustomerId(),
          inspection.getAssetId(),
          categoryCode,
          assetLabel,
          finding.getDefectLabel(),
          finding.getSeverity().name(),
          finding.getTechnicalNotes(),
          vector,
          java.sql.Timestamp.from(publishedAt));
    }
    jdbcTemplate.update(
        """
        UPDATE historical_finding_cases h
        SET report_outcome = v.resolution_status
        FROM reinspection_defect_verifications v
        WHERE v.reinspection_id = ?
          AND h.source_finding_id = v.source_finding_id
        """,
        inspectionId);
    backfillMissingChunkEmbeddings();
  }

  @EventListener(ApplicationReadyEvent.class)
  void scheduleStartupBackfill() {
    if (!knowledgeAiEnabled || advisorPort.isEmpty()) {
      return;
    }
    Thread.ofVirtual()
        .name("knowledge-rag-backfill")
        .start(
            () -> {
              // Retry briefly in case the local ai-inference container is still loading ONNX
              // weights.
              for (int attempt = 0; attempt < 4; attempt++) {
                try {
                  int remaining = backfillMissingEmbeddings();
                  if (remaining == 0) {
                    return;
                  }
                  Thread.sleep(5_000L);
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                  return;
                } catch (RuntimeException ignored) {
                  return;
                }
              }
            });
  }

  /**
   * Generates pgvector embeddings for any knowledge chunks or published historical cases that were
   * inserted without one (for example seed data or Flyway backfills). Returns the count of rows
   * still missing an embedding so startup callers know whether to retry.
   */
  @Transactional
  public int backfillMissingEmbeddings() {
    if (advisorPort.isEmpty()) {
      return 0;
    }
    int missing = backfillMissingChunkEmbeddings();
    List<CaseEmbedRow> unembeddedCases =
        jdbcTemplate.query(
            """
            SELECT id, defect_label, severity, technical_notes
            FROM historical_finding_cases
            WHERE embedding IS NULL
            """,
            (rs, rowNum) ->
                new CaseEmbedRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("defect_label"),
                    rs.getString("severity"),
                    rs.getString("technical_notes")));
    for (CaseEmbedRow item : unembeddedCases) {
      String text =
          cleanText(item.defectLabel(), "")
              + " "
              + cleanText(item.severity(), "")
              + " "
              + cleanText(item.technicalNotes(), "");
      Optional<String> vector = embed(text).map(HistoricalCaseService::toPgVectorLiteral);
      if (vector.isPresent()) {
        jdbcTemplate.update(
            "UPDATE historical_finding_cases SET embedding = CAST(? AS vector) WHERE id = ?",
            vector.get(),
            item.id());
      } else {
        missing++;
      }
    }
    return missing;
  }

  private int backfillMissingChunkEmbeddings() {
    if (advisorPort.isEmpty()) {
      return 0;
    }
    int missing = 0;
    List<ChunkEmbedRow> chunks =
        jdbcTemplate.query(
            """
            SELECT id, standard_code, title, defect_patterns, severity, guidance
            FROM inspection_knowledge_chunks
            WHERE embedding IS NULL
            """,
            (rs, rowNum) ->
                new ChunkEmbedRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("standard_code"),
                    rs.getString("title"),
                    rs.getString("defect_patterns"),
                    rs.getString("severity"),
                    rs.getString("guidance")));
    for (ChunkEmbedRow chunk : chunks) {
      String text =
          cleanText(chunk.standardCode(), "")
              + " "
              + cleanText(chunk.title(), "")
              + " "
              + cleanText(chunk.defectPatterns(), "")
              + " "
              + cleanText(chunk.severity(), "")
              + " "
              + cleanText(chunk.guidance(), "");
      Optional<String> vector = embed(text).map(HistoricalCaseService::toPgVectorLiteral);
      if (vector.isPresent()) {
        jdbcTemplate.update(
            "UPDATE inspection_knowledge_chunks SET embedding = CAST(? AS vector) WHERE id = ?",
            vector.get(),
            chunk.id());
      } else {
        missing++;
      }
    }
    return missing;
  }

  private record CaseEmbedRow(
      UUID id, String defectLabel, String severity, String technicalNotes) {}

  private record ChunkEmbedRow(
      UUID id,
      String standardCode,
      String title,
      String defectPatterns,
      String severity,
      String guidance) {}

  /**
   * Concise, source-backed references for an executive summary: matched guidelines and matched
   * anonymized/tenant cases. Both kinds come from the same tenant-safe retrieval path.
   */
  @Transactional(readOnly = true)
  public List<KnowledgeReferenceSummary> findKnowledgeReferences(UUID inspectionId) {
    if (inspectionId == null) {
      return List.of();
    }
    Inspection inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection == null) {
      return List.of();
    }
    String queryText =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
                .filter(Finding::isOfficial)
                .map(HistoricalCaseService::describeFinding)
                .collect(Collectors.joining(" "))
            + " "
            + resolveCategoryCode(inspection.getAssetId());
    KnowledgeRecommendationResponse matches = retrieve(inspection, queryText, 1, 2);

    List<KnowledgeReferenceSummary> references = new ArrayList<>();
    for (GuidelineMatch guideline : matches.guidelines()) {
      references.add(
          new KnowledgeReferenceSummary(
              KIND_GUIDELINE,
              guideline.standardCode(),
              truncate(guideline.title() + ": " + guideline.guidance(), 240),
              guideline.similarity()));
    }
    for (CaseMatch matchedCase : matches.cases()) {
      references.add(
          new KnowledgeReferenceSummary(
              KIND_CASE,
              matchedCase.defectLabel(),
              truncate(matchedCase.technicalNotes(), 240),
              matchedCase.similarity()));
    }
    return references;
  }

  Optional<float[]> embed(String text) {
    try {
      return advisorPort
          .flatMap(port -> port.embed(text))
          .filter(vector -> vector.length == EMBEDDING_DIMENSIONS);
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private String resolveCategoryCode(UUID assetId) {
    if (assetId == null) {
      return ALL_CATEGORY;
    }
    String code =
        jdbcTemplate.query(
            """
            SELECT c.code FROM assets a
            JOIN asset_categories c ON c.id = a.category_id
            WHERE a.id = ?
            """,
            rs -> rs.next() ? rs.getString(1) : null,
            assetId);
    return code == null || code.isBlank() ? ALL_CATEGORY : code;
  }

  private static String resolveAssetLabel(
      JdbcTemplate jdbcTemplate, UUID assetId, String categoryCode) {
    if (assetId == null) {
      return categoryCode;
    }
    String name =
        jdbcTemplate.query(
            "SELECT name FROM assets WHERE id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            assetId);
    if (name == null || name.isBlank()) {
      return categoryCode + " asset";
    }
    return name.length() <= 255 ? name : name.substring(0, 255);
  }

  private String resolveAssetLabel(UUID assetId, String categoryCode) {
    return resolveAssetLabel(jdbcTemplate, assetId, categoryCode);
  }

  private CaseRow mapCaseRow(ResultSet rs, int rowNum) throws SQLException {
    java.sql.Timestamp published = rs.getTimestamp("published_at");
    return new CaseRow(
        rs.getObject("id", UUID.class),
        rs.getObject("asset_id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        rs.getString("asset_category_code"),
        rs.getString("asset_label"),
        rs.getString("defect_label"),
        rs.getString("severity"),
        rs.getString("technical_notes"),
        rs.getString("report_outcome"),
        rs.getBoolean("is_anonymized_benchmark"),
        published == null ? null : published.toInstant(),
        rs.getObject("similarity", Double.class));
  }

  private GuidelineRow mapGuidelineRow(ResultSet rs, int rowNum) throws SQLException {
    return new GuidelineRow(
        rs.getObject("id", UUID.class),
        rs.getString("standard_code"),
        rs.getString("title"),
        rs.getString("severity"),
        rs.getString("guidance"),
        rs.getString("reference_url"),
        rs.getString("asset_category_code"),
        rs.getString("defect_patterns"),
        rs.getObject("similarity", Double.class));
  }

  static List<CaseMatch> rankHybrid(
      List<CaseRow> rows,
      String queryText,
      String categoryCode,
      UUID organizationId,
      UUID assetId,
      int limit) {
    Set<String> queryTokens = tokens(queryText);
    String querySeverity = severityIn(queryText);
    return rows.stream()
        .map(
            row ->
                new CaseScored(
                    row,
                    hybridScore(
                        row, queryTokens, querySeverity, categoryCode, organizationId, assetId)))
        .sorted(
            Comparator.comparingDouble(CaseScored::score)
                .reversed()
                .thenComparing(
                    scored -> scored.row().publishedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(scored -> scored.row().id().toString()))
        .limit(limit)
        .map(
            scored ->
                toCaseMatch(
                    scored.row(),
                    organizationId,
                    assetId,
                    normalizeHybridScore(scored.score(), HYBRID_CASE_MAX)))
        .toList();
  }

  static List<GuidelineMatch> rankGuidelinesHybrid(
      List<GuidelineRow> rows, String queryText, String categoryCode, int limit) {
    Set<String> queryTokens = tokens(queryText);
    String querySeverity = severityIn(queryText);
    return rows.stream()
        .map(
            row ->
                new GuidelineScored(
                    row, guidelineScore(row, queryTokens, querySeverity, categoryCode)))
        .sorted(
            Comparator.comparingDouble(GuidelineScored::score)
                .reversed()
                .thenComparing(scored -> scored.row().standardCode())
                .thenComparing(scored -> scored.row().id().toString()))
        .limit(limit)
        .map(
            scored ->
                toGuidelineMatch(
                    scored.row(), normalizeHybridScore(scored.score(), HYBRID_GUIDELINE_MAX)))
        .toList();
  }

  static double hybridScore(
      CaseRow row,
      Set<String> queryTokens,
      String querySeverity,
      String categoryCode,
      UUID organizationId,
      UUID assetId) {
    double score = categoryScore(categoryCode, row.assetCategoryCode());
    long overlap = queryTokens.stream().filter(tokens(row.defectLabel())::contains).count();
    score += Math.min(overlap, 3) * 2.0d;
    if (assetId != null && assetId.equals(row.assetId())) {
      score += 2.0d;
    }
    if (organizationId != null && organizationId.equals(row.organizationId())) {
      score += 1.0d;
    }
    return score + severityProximity(querySeverity, row.severity());
  }

  static double guidelineScore(
      GuidelineRow row, Set<String> queryTokens, String querySeverity, String categoryCode) {
    double score = categoryScore(categoryCode, row.assetCategoryCode());
    long overlap = queryTokens.stream().filter(tokens(row.defectPatterns())::contains).count();
    return score + Math.min(overlap, 3) * 2.0d + severityProximity(querySeverity, row.severity());
  }

  private static double categoryScore(String categoryCode, String rowCategoryCode) {
    if (categoryCode != null && categoryCode.equals(rowCategoryCode)) {
      return 3.0d;
    }
    return ALL_CATEGORY.equals(rowCategoryCode) ? 1.0d : 0.0d;
  }

  static double severityProximity(String querySeverity, String severity) {
    int queryRank = severityRank(querySeverity);
    int caseRank = severityRank(severity);
    if (queryRank == 0 || caseRank == 0) {
      return 0.0d;
    }
    int gap = Math.abs(queryRank - caseRank);
    return gap == 0 ? 2.0d : gap == 1 ? 1.0d : gap == 2 ? 0.5d : 0.0d;
  }

  static Set<String> tokens(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
        .filter(token -> token.length() >= 3)
        .collect(Collectors.toSet());
  }

  static String severityIn(String text) {
    if (text == null) {
      return null;
    }
    String upper = text.toUpperCase(Locale.ROOT);
    return Arrays.stream(new String[] {"CRITICAL", "HIGH", "MEDIUM", "LOW"})
        .filter(upper::contains)
        .findFirst()
        .orElse(null);
  }

  private static int severityRank(String severity) {
    if (severity == null) {
      return 0;
    }
    return switch (severity.toUpperCase(Locale.ROOT)) {
      case "LOW" -> 1;
      case "MEDIUM" -> 2;
      case "HIGH" -> 3;
      case "CRITICAL" -> 4;
      default -> 0;
    };
  }

  static double normalizeSimilarity(Double value) {
    if (value == null || !Double.isFinite(value)) {
      return 0.0d;
    }
    return Math.max(0.0d, Math.min(1.0d, value));
  }

  static double normalizeHybridScore(double score, double theoreticalMax) {
    if (theoreticalMax <= 0.0d) {
      return 0.0d;
    }
    return normalizeSimilarity(score / theoreticalMax);
  }

  /**
   * Renders a finite 384-d vector as a pgvector literal with a locale-independent decimal point.
   */
  public static String toPgVectorLiteral(float[] vector) {
    if (vector == null || vector.length == 0) {
      return "[]";
    }
    StringBuilder literal = new StringBuilder(vector.length * 10 + 2).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        literal.append(',');
      }
      float value = vector[i];
      literal.append(Float.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "0");
    }
    return literal.append(']').toString();
  }

  private static String describeFinding(Finding finding) {
    return finding.getDefectLabel()
        + " "
        + finding.getSeverity().name()
        + " "
        + (finding.getTechnicalNotes() == null ? "" : finding.getTechnicalNotes());
  }

  private static CaseMatch toCaseMatch(
      CaseRow row, UUID organizationId, UUID assetId, double similarity) {
    return new CaseMatch(
        row.id(),
        row.defectLabel(),
        row.severity(),
        row.assetCategoryCode(),
        row.assetLabel(),
        assetId != null && assetId.equals(row.assetId()),
        organizationId != null && organizationId.equals(row.organizationId()),
        similarity,
        row.technicalNotes(),
        row.reportOutcome(),
        row.publishedAt());
  }

  private static GuidelineMatch toGuidelineMatch(GuidelineRow row, double similarity) {
    return new GuidelineMatch(
        row.id(),
        row.standardCode(),
        row.title(),
        row.severity(),
        similarity,
        row.guidance(),
        row.referenceUrl());
  }

  private static String truncate(String text, int max) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return text.length() <= max ? text : text.substring(0, max);
  }

  /** Flattened case projection; {@code similarity} is populated by the pgvector query only. */
  record CaseRow(
      UUID id,
      UUID assetId,
      UUID organizationId,
      String assetCategoryCode,
      String assetLabel,
      String defectLabel,
      String severity,
      String technicalNotes,
      String reportOutcome,
      boolean anonymizedBenchmark,
      Instant publishedAt,
      Double similarity) {}

  record GuidelineRow(
      UUID id,
      String standardCode,
      String title,
      String severity,
      String guidance,
      String referenceUrl,
      String assetCategoryCode,
      String defectPatterns,
      Double similarity) {}

  private record CaseScored(CaseRow row, double score) {}

  private record GuidelineScored(GuidelineRow row, double score) {}
}
