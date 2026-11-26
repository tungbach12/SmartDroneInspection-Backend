package com.smartdroneinspection.inspections.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionListItemResponseTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID ASSET_ID = UUID.randomUUID();
  private static final UUID INSPECTOR_ID = UUID.randomUUID();
  private static final UUID REPORT_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void allTelescopingConstructorsPreserveDefaults() {
    var full = fullResponse();
    assertThat(full.ownerCustomerName()).isNull();
    assertThat(full.flightPermitVerificationStatus()).isEqualTo("APPROVED");

    var withVerification =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL",
            "Objective",
            "Bridge",
            "BR-1",
            "Hanoi",
            true,
            "PAID",
            1000L,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "PAID",
            "PAYOS",
            false,
            NOW,
            "ACCEPTED",
            NOW,
            "APPROVED",
            null,
            "Customer",
            "Inspector",
            "Manager",
            "M350",
            "SN-1");
    assertThat(withVerification.droneModel()).isEqualTo("M350");
    assertThat(withVerification.flightPermitVerificationStatus()).isEqualTo("APPROVED");
    assertThat(withVerification.ownerCustomerAvatarUrl()).isNull();
    assertThat(withVerification.inspectorAvatarUrl()).isNull();

    var withAvatars =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL",
            "Objective",
            "Bridge",
            "BR-1",
            "Hanoi",
            true,
            "PAID",
            1000L,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "PAID",
            "PAYOS",
            false,
            NOW,
            "ACCEPTED",
            NOW,
            "APPROVED",
            null,
            "Customer",
            "Inspector",
            "Manager",
            "M350",
            "SN-1",
            "/api/media/customer-avatar.png",
            "/api/media/inspector-avatar.png");
    assertThat(withAvatars.ownerCustomerAvatarUrl()).isEqualTo("/api/media/customer-avatar.png");
    assertThat(withAvatars.inspectorAvatarUrl()).isEqualTo("/api/media/inspector-avatar.png");

    var withoutSource =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL",
            "Objective",
            "Bridge",
            "BR-1",
            "Hanoi",
            true,
            "PAID",
            1000L,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "PAID",
            "PAYOS",
            false,
            NOW,
            "ACCEPTED",
            NOW,
            "APPROVED");
    assertThat(withoutSource.sourceInspectionId()).isNull();
    assertThat(withoutSource.inspectorName()).isNull();

    var withoutVerification =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL",
            "Objective",
            "Bridge",
            "BR-1",
            "Hanoi",
            true,
            "PAID",
            1000L,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "PAID",
            "PAYOS",
            false,
            NOW,
            "ACCEPTED",
            NOW);
    assertThat(withoutVerification.flightPermitVerificationStatus()).isEqualTo("NOT_UPLOADED");

    var withoutAssignment =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL",
            "Objective",
            "Bridge",
            "BR-1",
            "Hanoi",
            true,
            "PAID",
            1000L,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "PAID",
            "PAYOS");
    assertThat(withoutAssignment.assignmentStatus()).isEqualTo("ACCEPTED");
    assertThat(withoutAssignment.reassignmentRequired()).isFalse();

    var withoutFinances =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL",
            "Bridge",
            "BR-1",
            "Hanoi",
            true,
            "PAID",
            1000L);
    assertThat(withoutFinances.commissionRate()).isNull();
    assertThat(withoutFinances.objective()).isNull();

    var minimal =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW",
            "INITIAL");
    assertThat(minimal.assetName()).isNull();
    assertThat(minimal.paymentStatus()).isNull();

    var shortest =
        new InspectionListItemResponse(
            ID,
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            "SCHEDULED",
            NOW,
            NOW,
            NOW,
            NOW,
            REPORT_ID,
            "DRAFT",
            1,
            "REVIEW");
    assertThat(shortest.inspectionType()).isEqualTo("INITIAL");
    assertThat(shortest.reportStatus()).isEqualTo("DRAFT");
  }

  private static InspectionListItemResponse fullResponse() {
    return new InspectionListItemResponse(
        ID,
        CUSTOMER_ID,
        ASSET_ID,
        INSPECTOR_ID,
        "SCHEDULED",
        NOW,
        NOW,
        NOW,
        NOW,
        REPORT_ID,
        "DRAFT",
        1,
        "REVIEW",
        "INITIAL",
        "Objective",
        "Bridge",
        "BR-1",
        "Hanoi",
        true,
        "PAID",
        1000L,
        BigDecimal.TEN,
        BigDecimal.ONE,
        BigDecimal.ONE,
        "PAID",
        "PAYOS",
        false,
        NOW,
        "ACCEPTED",
        NOW,
        "APPROVED",
        null);
  }
}
