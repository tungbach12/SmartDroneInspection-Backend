package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.Attestations;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.ComplianceSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.CredentialSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.DroneDocumentSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.InspectionSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.PairSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.PermitSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.PreparationSource;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory.ReadinessSnapshotInput;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReadinessSnapshotFactoryTest {

  private static final UUID INSPECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PREPARATION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID REVIEWER_CREDENTIAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID INSPECTOR_CREDENTIAL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
  private static final UUID PERMIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
  private static final UUID PAIR_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
  private static final UUID ORGANIZATION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000008");
  private static final UUID ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final UUID INSPECTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID DRONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
  private static final Instant PLANNED_START = Instant.parse("2026-06-01T09:00:00Z");

  private final ReadinessSnapshotFactory factory =
      new ReadinessSnapshotFactory(JsonMapper.builder().build());

  @Test
  void equivalentSourcesInDifferentListOrderProduceIdenticalSnapshotsAndHash() {
    UUID secondCredentialId = UUID.fromString("00000000-0000-0000-0000-000000000014");
    UUID secondDocumentId = UUID.fromString("00000000-0000-0000-0000-000000000015");
    CredentialSource firstCredential =
        credential(INSPECTOR_CREDENTIAL_ID, Instant.parse("2027-01-01T00:00:00Z"));
    CredentialSource secondCredential =
        credential(secondCredentialId, Instant.parse("2028-01-01T00:00:00Z"));
    DroneDocumentSource firstDocument = document(DOCUMENT_ID);
    DroneDocumentSource secondDocument = document(secondDocumentId);

    ReadinessSnapshotFactory.Snapshot first =
        factory.create(
            approvalInput(
                List.of(firstCredential, secondCredential),
                List.of(firstDocument, secondDocument)));
    ReadinessSnapshotFactory.Snapshot reordered =
        factory.create(
            approvalInput(
                List.of(secondCredential, firstCredential),
                List.of(secondDocument, firstDocument)));

    assertThat(first.permitSnapshot()).isEqualTo(reordered.permitSnapshot());
    assertThat(first.credentialSnapshot()).isEqualTo(reordered.credentialSnapshot());
    assertThat(first.droneDocumentSnapshot()).isEqualTo(reordered.droneDocumentSnapshot());
    assertThat(first.sourceHash()).isEqualTo(reordered.sourceHash());
  }

  @Test
  void sourceHashIsSha256OfTheDocumentedCanonicalUtf8Payload() {
    ReadinessSnapshotFactory.Snapshot snapshot = factory.create(minimalInput());
    String literalCanonicalPayload =
        "{\"decision\":\"APPROVED\",\"permitSnapshot\":\"{\\\"permits\\\":[],\\\"blockers\\\":[],\\\"requires_human_verification\\\":false,\\\"human_verification_basis\\\":[],\\\"source_envelope\\\":{\\\"reviewer_id\\\":\\\"00000000-0000-0000-0000-000000000003\\\",\\\"inspection_id\\\":null,\\\"organization_id\\\":null,\\\"asset_id\\\":null,\\\"objective\\\":null,\\\"scope\\\":null,\\\"component_scope\\\":null,\\\"acceptance_criteria\\\":null,\\\"schedule_id\\\":null,\\\"due_cycle_key\\\":null,\\\"planned_start_at\\\":null,\\\"planned_end_at\\\":null,\\\"asset_pair\\\":null,\\\"preparation\\\":null,\\\"observed_source_set\\\":null,\\\"decision_reason\\\":null}}\","
            + "\"permitSnapshotIds\":\"[]\",\"credentialSnapshot\":\"{\\\"reviewer\\\":null,\\\"inspectors\\\":[],\\\"attestations\\\":{\\\"applicabilityComplete\\\":false,\\\"applicabilityBasisReference\\\":null,\\\"noInspectorCredentialReason\\\":null,\\\"noDroneDocumentReason\\\":null,\\\"humanVerificationBasis\\\":null}}\","
            + "\"credentialSnapshotIds\":\"[]\",\"droneDocumentSnapshot\":\"[]\",\"droneDocumentSnapshotIds\":\"[]\"}";

    assertThat(snapshot.sourceHash()).isEqualTo(sha256(literalCanonicalPayload));
    assertThat(snapshot.sourceHash()).matches("[0-9a-f]{64}");
  }

  @Test
  void changingMaterialSourceValuesChangesTheHash() {
    ReadinessSnapshotInput originalInput = approvalInputWithSources();
    String originalHash = factory.create(originalInput).sourceHash();

    assertThat(
            factory
                .create(withCredentialExpiry(originalInput, Instant.parse("2028-01-01T00:00:00Z")))
                .sourceHash())
        .isNotEqualTo(originalHash);
    assertThat(factory.create(withPermitStatus(originalInput, "EXPIRED")).sourceHash())
        .isNotEqualTo(originalHash);
    assertThat(
            factory
                .create(withAttestations(originalInput, "new applicability basis", "visual basis"))
                .sourceHash())
        .isNotEqualTo(originalHash);
    assertThat(
            factory.create(withInspectionScope(originalInput, "{\"zone\":\"south\"}")).sourceHash())
        .isNotEqualTo(originalHash);
    assertThat(
            factory
                .create(withPairResponseTime(originalInput, Instant.parse("2026-06-01T09:00:00Z")))
                .sourceHash())
        .isNotEqualTo(originalHash);
    assertThat(factory.create(withPreparationVersion(originalInput, 2)).sourceHash())
        .isNotEqualTo(originalHash);
  }

  @Test
  void permitSnapshotEnvelopeRetainsInspectionPairAndSubmittedPreparationSources() {
    ReadinessSnapshotFactory.Snapshot snapshot = factory.create(approvalInputWithSources());

    assertThat(snapshot.permitSnapshot())
        .contains("\"objective\":\"Inspect main span\"")
        .contains("\"scope\":{\"zone\":\"north\"}")
        .contains("\"component_scope\":{\"components\":[\"span\"]}")
        .contains("\"acceptance_criteria\":[\"no crack\"]")
        .contains("\"schedule_id\":\"00000000-0000-0000-0000-000000000010\"")
        .contains("\"due_cycle_key\":\"2026-Q2\"")
        .contains("\"planned_start_at\":\"2026-06-01T09:00:00Z\"")
        .contains("\"responded_at\":\"2026-06-01T08:00:00Z\"")
        .contains("\"shot_list\":{\"shots\":[\"span\"]}")
        .contains("\"preparation_version\":1");
    assertThat(snapshot.permitSnapshotIds()).contains(PERMIT_ID.toString());
  }

  @Test
  void returnedSnapshotPreservesObservedAndUnresolvedIdsAndIsNeverComplete() {
    UUID unresolvedId = UUID.fromString("00000000-0000-0000-0000-000000000099");
    ReadinessSnapshotFactory.Snapshot snapshot =
        factory.create(
            returnInput(
                List.of(INSPECTOR_CREDENTIAL_ID), List.of(DOCUMENT_ID), List.of(unresolvedId)));

    assertThat(snapshot.permitSnapshot())
        .contains("\"observed_inspector_credential_ids\":[\"" + INSPECTOR_CREDENTIAL_ID + "\"]")
        .contains("\"observed_drone_document_ids\":[\"" + DOCUMENT_ID + "\"]")
        .contains("\"unresolved_source_ids\":[\"" + unresolvedId + "\"]")
        .contains("\"observed_source_set_complete\":false");
  }

  @Test
  void returnedSnapshotWithEmptyObservedIdsDoesNotClaimCompleteness() {
    ReadinessSnapshotFactory.Snapshot snapshot =
        factory.create(returnInput(List.of(), List.of(), List.of()));

    assertThat(snapshot.permitSnapshot())
        .contains("\"observed_inspector_credential_ids\":[]")
        .contains("\"observed_drone_document_ids\":[]")
        .contains("\"unresolved_source_ids\":[]")
        .contains("\"observed_source_set_complete\":false");
  }

  private ReadinessSnapshotInput minimalInput() {
    return new ReadinessSnapshotInput(
        ReadinessDecisionType.APPROVED,
        REVIEWER_CREDENTIAL_ID,
        null,
        null,
        null,
        new ComplianceSource(List.of(), List.of(), List.of(), false, List.of()),
        null,
        List.of(),
        List.of(),
        new Attestations(false, null, null, null, null),
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  private ReadinessSnapshotInput approvalInputWithSources() {
    return approvalInput(
        List.of(credential(INSPECTOR_CREDENTIAL_ID, Instant.parse("2027-01-01T00:00:00Z"))),
        List.of(document(DOCUMENT_ID)));
  }

  private ReadinessSnapshotInput approvalInput(
      List<CredentialSource> credentials, List<DroneDocumentSource> documents) {
    return new ReadinessSnapshotInput(
        ReadinessDecisionType.APPROVED,
        REVIEWER_CREDENTIAL_ID,
        inspection("{\"zone\":\"north\"}"),
        preparation(1),
        pair(Instant.parse("2026-06-01T08:00:00Z")),
        new ComplianceSource(
            List.of(permitSource("ACTIVE")), List.of(), List.of(PERMIT_ID), false, List.of()),
        credential(REVIEWER_CREDENTIAL_ID, Instant.parse("2027-01-01T00:00:00Z")),
        credentials,
        documents,
        new Attestations(
            true,
            "traceable basis",
            "no inspector credential",
            "no Drone document",
            "visual basis"),
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  private ReadinessSnapshotInput returnInput(
      List<UUID> observedCredentialIds, List<UUID> observedDocumentIds, List<UUID> unresolvedIds) {
    return new ReadinessSnapshotInput(
        ReadinessDecisionType.RETURNED,
        REVIEWER_CREDENTIAL_ID,
        inspection("{\"zone\":\"north\"}"),
        preparation(1),
        pair(Instant.parse("2026-06-01T08:00:00Z")),
        new ComplianceSource(List.of(), List.of(), List.of(), false, List.of()),
        credential(REVIEWER_CREDENTIAL_ID, Instant.parse("2027-01-01T00:00:00Z")),
        List.of(credential(INSPECTOR_CREDENTIAL_ID, Instant.parse("2027-01-01T00:00:00Z"))),
        List.of(document(DOCUMENT_ID)),
        new Attestations(false, null, null, null, null),
        observedCredentialIds,
        observedDocumentIds,
        unresolvedIds,
        "Needs updated inspection evidence");
  }

  private ReadinessSnapshotInput withCredentialExpiry(
      ReadinessSnapshotInput input, Instant expiry) {
    return new ReadinessSnapshotInput(
        input.decision(),
        input.reviewerId(),
        input.inspection(),
        input.preparation(),
        input.pair(),
        input.compliance(),
        input.reviewerCredential(),
        List.of(credential(INSPECTOR_CREDENTIAL_ID, expiry)),
        input.droneDocuments(),
        input.attestations(),
        input.observedInspectorCredentialIds(),
        input.observedDroneDocumentIds(),
        input.unresolvedSourceIds(),
        input.reason());
  }

  private ReadinessSnapshotInput withPermitStatus(ReadinessSnapshotInput input, String status) {
    PermitSource permit = permitSource(status);
    return new ReadinessSnapshotInput(
        input.decision(),
        input.reviewerId(),
        input.inspection(),
        input.preparation(),
        input.pair(),
        new ComplianceSource(List.of(permit), List.of(), List.of(PERMIT_ID), false, List.of()),
        input.reviewerCredential(),
        input.inspectorCredentials(),
        input.droneDocuments(),
        input.attestations(),
        input.observedInspectorCredentialIds(),
        input.observedDroneDocumentIds(),
        input.unresolvedSourceIds(),
        input.reason());
  }

  private ReadinessSnapshotInput withAttestations(
      ReadinessSnapshotInput input, String basis, String human) {
    return new ReadinessSnapshotInput(
        input.decision(),
        input.reviewerId(),
        input.inspection(),
        input.preparation(),
        input.pair(),
        input.compliance(),
        input.reviewerCredential(),
        input.inspectorCredentials(),
        input.droneDocuments(),
        new Attestations(true, basis, "no inspector credential", "no Drone document", human),
        input.observedInspectorCredentialIds(),
        input.observedDroneDocumentIds(),
        input.unresolvedSourceIds(),
        input.reason());
  }

  private ReadinessSnapshotInput withInspectionScope(ReadinessSnapshotInput input, String scope) {
    InspectionSource old = input.inspection();
    InspectionSource changed =
        new InspectionSource(
            old.id(),
            old.organizationId(),
            old.assetId(),
            old.scheduleId(),
            old.dueCycleKey(),
            old.objective(),
            scope,
            old.componentScope(),
            old.acceptanceCriteria(),
            old.plannedStartAt(),
            old.plannedEndAt());
    return new ReadinessSnapshotInput(
        input.decision(),
        input.reviewerId(),
        changed,
        input.preparation(),
        input.pair(),
        input.compliance(),
        input.reviewerCredential(),
        input.inspectorCredentials(),
        input.droneDocuments(),
        input.attestations(),
        input.observedInspectorCredentialIds(),
        input.observedDroneDocumentIds(),
        input.unresolvedSourceIds(),
        input.reason());
  }

  private ReadinessSnapshotInput withPairResponseTime(
      ReadinessSnapshotInput input, Instant respondedAt) {
    PairSource old = input.pair();
    PairSource changed =
        new PairSource(
            old.id(),
            old.organizationId(),
            old.assetId(),
            old.inspectorUserId(),
            old.droneId(),
            old.status(),
            old.validFrom(),
            old.validUntil(),
            old.assignmentResponse(),
            respondedAt);
    return new ReadinessSnapshotInput(
        input.decision(),
        input.reviewerId(),
        input.inspection(),
        input.preparation(),
        changed,
        input.compliance(),
        input.reviewerCredential(),
        input.inspectorCredentials(),
        input.droneDocuments(),
        input.attestations(),
        input.observedInspectorCredentialIds(),
        input.observedDroneDocumentIds(),
        input.unresolvedSourceIds(),
        input.reason());
  }

  private ReadinessSnapshotInput withPreparationVersion(ReadinessSnapshotInput input, int version) {
    PreparationSource old = input.preparation();
    PreparationSource changed =
        new PreparationSource(
            old.id(),
            version,
            old.status(),
            old.shotList(),
            old.evidenceTypes(),
            old.accessConstraints(),
            old.safetyObservations(),
            old.permitDocumentReferences(),
            old.submittedAt());
    return new ReadinessSnapshotInput(
        input.decision(),
        input.reviewerId(),
        input.inspection(),
        changed,
        input.pair(),
        input.compliance(),
        input.reviewerCredential(),
        input.inspectorCredentials(),
        input.droneDocuments(),
        input.attestations(),
        input.observedInspectorCredentialIds(),
        input.observedDroneDocumentIds(),
        input.unresolvedSourceIds(),
        input.reason());
  }

  private InspectionSource inspection(String scope) {
    return new InspectionSource(
        INSPECTION_ID,
        ORGANIZATION_ID,
        ASSET_ID,
        UUID.fromString("00000000-0000-0000-0000-000000000010"),
        "2026-Q2",
        "Inspect main span",
        scope,
        "{\"components\":[\"span\"]}",
        "[\"no crack\"]",
        PLANNED_START,
        Instant.parse("2026-06-01T10:00:00Z"));
  }

  private PreparationSource preparation(int version) {
    return new PreparationSource(
        PREPARATION_ID,
        version,
        "SUBMITTED",
        "{\"shots\":[\"span\"]}",
        "[\"RGB\"]",
        "[\"access north\"]",
        "no visible hazard",
        "[\"permit\"]",
        Instant.parse("2026-05-31T12:00:00Z"));
  }

  private PairSource pair(Instant respondedAt) {
    return new PairSource(
        PAIR_ID,
        ORGANIZATION_ID,
        ASSET_ID,
        INSPECTOR_ID,
        DRONE_ID,
        "ACTIVE",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2027-01-01T00:00:00Z"),
        "ACCEPTED",
        respondedAt);
  }

  private PermitSource permitSource(String status) {
    return new PermitSource(
        PERMIT_ID,
        ORGANIZATION_ID,
        ASSET_ID,
        "AVIATION",
        "Aviation Authority",
        "PERMIT-1",
        "North Zone",
        "{\"zone\":\"north\"}",
        PLANNED_START,
        Instant.parse("2027-01-01T00:00:00Z"),
        status,
        status.equals("NOT_APPLICABLE") ? "reviewed exemption basis" : null);
  }

  private CredentialSource credential(UUID id, Instant expiresAt) {
    return new CredentialSource(
        id,
        ORGANIZATION_ID,
        INSPECTOR_ID,
        "PILOT",
        "Authority",
        "LIC-7",
        Instant.parse("2025-01-01T00:00:00Z"),
        expiresAt,
        "ACTIVE",
        UUID.fromString("00000000-0000-0000-0000-000000000020"),
        REVIEWER_CREDENTIAL_ID,
        Instant.parse("2025-02-01T00:00:00Z"),
        "verified against evidence");
  }

  private DroneDocumentSource document(UUID id) {
    return new DroneDocumentSource(
        id,
        DRONE_ID,
        "AIRWORTHINESS",
        "Authority",
        "DOC-7",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2027-01-01T00:00:00Z"),
        "ACTIVE",
        REVIEWER_CREDENTIAL_ID,
        Instant.parse("2026-01-01T00:00:00Z"),
        INSPECTOR_ID,
        Instant.parse("2026-01-01T00:00:00Z"),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }
}
