package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Builds deterministic V25 snapshots and a hash over their complete source payload. */
@Component
public final class ReadinessSnapshotFactory {

  private final ObjectMapper objectMapper;

  public ReadinessSnapshotFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Snapshot create(ReadinessSnapshotInput input) {
    String permitSnapshot = write(permitSnapshot(input));
    String permitSnapshotIds = write(sortedIds(input.compliance().linkedPermitIds()));
    String credentialSnapshot = write(credentialSnapshot(input));
    String credentialSnapshotIds = write(credentialIds(input));
    String droneDocumentSnapshot = write(droneDocumentSnapshot(input));
    String droneDocumentSnapshotIds = write(documentIds(input));
    CanonicalPayload payload =
        new CanonicalPayload(
            input.decision().name(),
            permitSnapshot,
            permitSnapshotIds,
            credentialSnapshot,
            credentialSnapshotIds,
            droneDocumentSnapshot,
            droneDocumentSnapshotIds);
    return new Snapshot(
        permitSnapshot,
        permitSnapshotIds,
        credentialSnapshot,
        credentialSnapshotIds,
        droneDocumentSnapshot,
        droneDocumentSnapshotIds,
        sha256(write(payload)));
  }

  private Map<String, Object> permitSnapshot(ReadinessSnapshotInput input) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put(
        "permits",
        input.compliance().permitSources().stream()
            .sorted(Comparator.comparing(PermitSource::id))
            .toList());
    snapshot.put(
        "blockers",
        input.compliance().blockers().stream()
            .sorted(
                Comparator.comparing(ReadinessFinding::code)
                    .thenComparing(ReadinessFinding::detail))
            .toList());
    snapshot.put("requires_human_verification", input.compliance().requiresHumanVerification());
    snapshot.put("human_verification_basis", input.compliance().humanVerificationBasis());
    snapshot.put("source_envelope", sourceEnvelope(input));
    return snapshot;
  }

  private Map<String, Object> sourceEnvelope(ReadinessSnapshotInput input) {
    InspectionSource inspection = input.inspection();
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("reviewer_id", input.reviewerId());
    envelope.put("inspection_id", inspection == null ? null : inspection.id());
    envelope.put("organization_id", inspection == null ? null : inspection.organizationId());
    envelope.put("asset_id", inspection == null ? null : inspection.assetId());
    envelope.put("objective", inspection == null ? null : inspection.objective());
    envelope.put("scope", inspection == null ? null : json(inspection.scope()));
    envelope.put("component_scope", inspection == null ? null : json(inspection.componentScope()));
    envelope.put(
        "acceptance_criteria", inspection == null ? null : json(inspection.acceptanceCriteria()));
    envelope.put("schedule_id", inspection == null ? null : inspection.scheduleId());
    envelope.put("due_cycle_key", inspection == null ? null : inspection.dueCycleKey());
    envelope.put("planned_start_at", inspection == null ? null : inspection.plannedStartAt());
    envelope.put("planned_end_at", inspection == null ? null : inspection.plannedEndAt());
    envelope.put("asset_pair", input.pair() == null ? null : pairSnapshot(input.pair()));
    envelope.put(
        "preparation",
        input.preparation() == null ? null : preparationSnapshot(input.preparation()));
    envelope.put(
        "observed_source_set",
        input.decision() == ReadinessDecisionType.RETURNED ? observedSourceSet(input) : null);
    envelope.put("decision_reason", input.reason());
    return envelope;
  }

  private Map<String, Object> pairSnapshot(PairSource pair) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("pair_id", pair.id());
    snapshot.put("organization_id", pair.organizationId());
    snapshot.put("asset_id", pair.assetId());
    snapshot.put("inspector_user_id", pair.inspectorUserId());
    snapshot.put("drone_id", pair.droneId());
    snapshot.put("status", pair.status());
    snapshot.put("valid_from", pair.validFrom());
    snapshot.put("valid_until", pair.validUntil());
    snapshot.put("assignment_response", pair.assignmentResponse());
    snapshot.put("responded_at", pair.respondedAt());
    return snapshot;
  }

  private Map<String, Object> preparationSnapshot(PreparationSource preparation) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("preparation_id", preparation.id());
    snapshot.put("preparation_version", preparation.preparationVersion());
    snapshot.put("status", preparation.status());
    snapshot.put("shot_list", json(preparation.shotList()));
    snapshot.put("evidence_types", json(preparation.evidenceTypes()));
    snapshot.put("access_constraints", json(preparation.accessConstraints()));
    snapshot.put("safety_observations", preparation.safetyObservations());
    snapshot.put("permit_document_references", json(preparation.permitDocumentReferences()));
    snapshot.put("submitted_at", preparation.submittedAt());
    return snapshot;
  }

  private Map<String, Object> observedSourceSet(ReadinessSnapshotInput input) {
    Map<String, Object> observed = new LinkedHashMap<>();
    observed.put(
        "observed_inspector_credential_ids", sortedIds(input.observedInspectorCredentialIds()));
    observed.put("observed_drone_document_ids", sortedIds(input.observedDroneDocumentIds()));
    observed.put("unresolved_source_ids", sortedIds(input.unresolvedSourceIds()));
    observed.put("observed_source_set_complete", false);
    return observed;
  }

  private CredentialSnapshot credentialSnapshot(ReadinessSnapshotInput input) {
    return new CredentialSnapshot(
        input.reviewerCredential(),
        input.inspectorCredentials().stream()
            .sorted(Comparator.comparing(CredentialSource::id))
            .toList(),
        input.attestations());
  }

  private List<UUID> credentialIds(ReadinessSnapshotInput input) {
    return input.inspectorCredentials().stream().map(CredentialSource::id).sorted().toList();
  }

  private List<DroneDocumentSource> droneDocumentSnapshot(ReadinessSnapshotInput input) {
    return input.droneDocuments().stream()
        .sorted(Comparator.comparing(DroneDocumentSource::id))
        .toList();
  }

  private List<UUID> documentIds(ReadinessSnapshotInput input) {
    return input.droneDocuments().stream().map(DroneDocumentSource::id).sorted().toList();
  }

  private List<UUID> sortedIds(List<UUID> ids) {
    return ids.stream().sorted().toList();
  }

  private Object json(String json) {
    if (json == null) return null;
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JacksonException exception) {
      return json;
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Readiness snapshot could not be serialized", exception);
    }
  }

  private String sha256(String canonicalPayload) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record Snapshot(
      String permitSnapshot,
      String permitSnapshotIds,
      String credentialSnapshot,
      String credentialSnapshotIds,
      String droneDocumentSnapshot,
      String droneDocumentSnapshotIds,
      String sourceHash) {}

  public record ReadinessSnapshotInput(
      ReadinessDecisionType decision,
      UUID reviewerId,
      InspectionSource inspection,
      PreparationSource preparation,
      PairSource pair,
      ComplianceSource compliance,
      CredentialSource reviewerCredential,
      List<CredentialSource> inspectorCredentials,
      List<DroneDocumentSource> droneDocuments,
      Attestations attestations,
      List<UUID> observedInspectorCredentialIds,
      List<UUID> observedDroneDocumentIds,
      List<UUID> unresolvedSourceIds,
      String reason) {

    public ReadinessSnapshotInput {
      inspectorCredentials = List.copyOf(inspectorCredentials);
      droneDocuments = List.copyOf(droneDocuments);
      observedInspectorCredentialIds = List.copyOf(observedInspectorCredentialIds);
      observedDroneDocumentIds = List.copyOf(observedDroneDocumentIds);
      unresolvedSourceIds = List.copyOf(unresolvedSourceIds);
    }
  }

  public record InspectionSource(
      UUID id,
      UUID organizationId,
      UUID assetId,
      UUID scheduleId,
      String dueCycleKey,
      String objective,
      String scope,
      String componentScope,
      String acceptanceCriteria,
      Instant plannedStartAt,
      Instant plannedEndAt) {}

  public record PreparationSource(
      UUID id,
      int preparationVersion,
      String status,
      String shotList,
      String evidenceTypes,
      String accessConstraints,
      String safetyObservations,
      String permitDocumentReferences,
      Instant submittedAt) {}

  public record ReadinessReturnCommand(
      UUID reviewerCredentialId,
      List<UUID> inspectorCredentialIdsObserved,
      List<UUID> droneDocumentIdsObserved,
      String reason) {

    public ReadinessReturnCommand {
      inspectorCredentialIdsObserved = List.copyOf(inspectorCredentialIdsObserved);
      droneDocumentIdsObserved = List.copyOf(droneDocumentIdsObserved);
    }
  }

  public record PairSource(
      UUID id,
      UUID organizationId,
      UUID assetId,
      UUID inspectorUserId,
      UUID droneId,
      String status,
      Instant validFrom,
      Instant validUntil,
      String assignmentResponse,
      Instant respondedAt) {}

  public record PermitSource(
      UUID id,
      UUID organizationId,
      UUID assetId,
      String permitType,
      String issuingAuthority,
      String permitReference,
      String areaReference,
      String geographicScope,
      Instant validFrom,
      Instant validUntil,
      String status,
      String legalBasis) {}

  public record ReadinessFinding(String code, String detail) {}

  public record ComplianceSource(
      List<PermitSource> permitSources,
      List<ReadinessFinding> blockers,
      List<UUID> linkedPermitIds,
      boolean requiresHumanVerification,
      List<String> humanVerificationBasis) {
    public ComplianceSource {
      permitSources = List.copyOf(permitSources);
      blockers = List.copyOf(blockers);
      linkedPermitIds = List.copyOf(linkedPermitIds);
      humanVerificationBasis = List.copyOf(humanVerificationBasis);
    }
  }

  public record CredentialSource(
      UUID id,
      UUID organizationId,
      UUID userId,
      String credentialType,
      String issuer,
      String credentialReference,
      Instant issuedAt,
      Instant expiresAt,
      String status,
      UUID evidenceId,
      UUID verifiedByUserId,
      Instant verifiedAt,
      String verificationReason) {}

  public record DroneDocumentSource(
      UUID id,
      UUID droneId,
      String documentType,
      String issuer,
      String documentReference,
      Instant validFrom,
      Instant validUntil,
      String status,
      UUID reviewedByUserId,
      Instant reviewedAt,
      UUID uploadedByUserId,
      Instant createdAt,
      String checksumSha256) {}

  public record Attestations(
      boolean applicabilityComplete,
      String applicabilityBasisReference,
      String noInspectorCredentialReason,
      String noDroneDocumentReason,
      String humanVerificationBasis) {}

  private record CredentialSnapshot(
      CredentialSource reviewer, List<CredentialSource> inspectors, Attestations attestations) {}

  private record CanonicalPayload(
      String decision,
      String permitSnapshot,
      String permitSnapshotIds,
      String credentialSnapshot,
      String credentialSnapshotIds,
      String droneDocumentSnapshot,
      String droneDocumentSnapshotIds) {}
}
