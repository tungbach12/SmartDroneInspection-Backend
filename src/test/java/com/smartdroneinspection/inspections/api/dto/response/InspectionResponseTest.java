package com.smartdroneinspection.inspections.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.AssignmentStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionResponseTest {

  @Test
  void mapsAllInspectionFieldsForTheApi() {
    Inspection inspection = inspection();
    InspectionResponse response = InspectionResponse.from(inspection);

    assertThat(response.id()).isEqualTo(inspection.getId());
    assertThat(response.ownerCustomerId()).isEqualTo(inspection.getOwnerCustomerId());
    assertThat(response.assetId()).isEqualTo(inspection.getAssetId());
    assertThat(response.inspectorId()).isEqualTo(inspection.getInspectorId());
    assertThat(response.droneId()).isEqualTo(inspection.getDroneId());
    assertThat(response.status()).isEqualTo("IN_PROGRESS");
    assertThat(response.inspectionType()).isEqualTo("RE_INSPECTION");
    assertThat(response.sourceInspectionId()).isNotNull();
    assertThat(response.reassignmentRequired()).isFalse();
    assertThat(response.declineReason()).isEqualTo("Conflict");
    assertThat(response.cancellationReason()).isEqualTo("Weather");
    assertThat(response.clarificationCount()).isEqualTo(2);
    assertThat(response.assignmentStatus()).isEqualTo("DECLINED");
    assertThat(response.flightPermitVerificationStatus()).isEqualTo("APPROVED");
    assertThat(response.flightPermitNumber()).isEqualTo("FP-1");
    assertThat(response.flightPermitMaxAltitudeM()).isEqualTo(120);
    assertThat(response.flightPermitFileSizeBytes()).isEqualTo(2048L);
    assertThat(response.flightPermitVerifiedBy()).isNotNull();
    assertThat(response.flightPermitReviewNotes()).isNull();
    assertThat(response.inspectionItems()).isEmpty();

    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.SCHEDULED);
    assertThat(InspectionResponse.from(inspection).reassignmentRequired()).isTrue();
  }

  @Test
  void legacyConstructorDerivesFlightPermitStatusAndDefensiveItemList() {
    InspectionResponse pending =
        new InspectionResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge",
            "Hanoi",
            null,
            null,
            null,
            UUID.randomUUID(),
            "Inspector",
            UUID.randomUUID(),
            "SN-1",
            "Mavic",
            "OPERATIONAL",
            "permits/fp.pdf",
            "fp.pdf",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "SCHEDULED",
            Instant.now(),
            Instant.now(),
            "INITIAL",
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            0,
            "ACCEPTED",
            null);

    assertThat(pending.flightPermitVerificationStatus()).isEqualTo("PENDING_REVIEW");
    assertThat(pending.inspectionItems()).isEmpty();

    InspectionResponse notUploaded =
        new InspectionResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge",
            "Hanoi",
            List.of("Roof"),
            UUID.randomUUID(),
            "Inspector",
            UUID.randomUUID(),
            "SN-1",
            "Mavic",
            "OPERATIONAL",
            null,
            null,
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "SCHEDULED",
            Instant.now(),
            Instant.now());

    assertThat(notUploaded.flightPermitVerificationStatus()).isEqualTo("NOT_UPLOADED");
    assertThat(notUploaded.inspectionType()).isEqualTo("INITIAL");
    assertThat(notUploaded.assignmentStatus()).isEqualTo("ACCEPTED");
    assertThat(notUploaded.inspectionItems()).containsExactly("Roof");

    InspectionResponse compact =
        new InspectionResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge",
            "Hanoi",
            new BigDecimal("21.0"),
            new BigDecimal("105.0"),
            List.of("Roof"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            null,
            "SCHEDULED",
            Instant.now(),
            Instant.now(),
            "INITIAL",
            null);

    assertThat(compact.inspectionItems()).containsExactly("Roof");
    assertThat(compact.flightPermitVerificationStatus()).isEqualTo("NOT_UPLOADED");
  }

  private static Inspection inspection() {
    Inspection inspection =
        new Inspection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    ReflectionTestUtils.setField(inspection, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
    ReflectionTestUtils.setField(inspection, "inspectionType", InspectionType.RE_INSPECTION);
    ReflectionTestUtils.setField(inspection, "sourceInspectionId", UUID.randomUUID());
    ReflectionTestUtils.setField(inspection, "declineReason", "Conflict");
    ReflectionTestUtils.setField(inspection, "declinedAt", Instant.now());
    ReflectionTestUtils.setField(inspection, "declinedByInspectorId", UUID.randomUUID());
    ReflectionTestUtils.setField(inspection, "cancellationReason", "Weather");
    ReflectionTestUtils.setField(inspection, "overdueNotifiedAt", Instant.now());
    ReflectionTestUtils.setField(inspection, "clarificationCount", 2);
    ReflectionTestUtils.setField(inspection, "assignmentStatus", AssignmentStatus.DECLINED);
    ReflectionTestUtils.setField(inspection, "assignmentAcceptedAt", Instant.now());
    inspection.recordFlightPermit(
        "permits/fp-1.pdf",
        "fp-1.pdf",
        "FP-1",
        "CAA",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        "SN-1",
        120,
        "abc",
        2048L,
        "application/pdf",
        Instant.now(),
        UUID.randomUUID());
    return inspection;
  }
}
