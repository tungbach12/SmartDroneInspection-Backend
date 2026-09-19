package com.smartdroneinspection.inspectionrequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspectionrequests.domain.InspectionQuotation;
import com.smartdroneinspection.inspectionrequests.domain.InspectionQuotationStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionQuotationTest {

  @Test
  void quotationNormalizesCurrencyAndStartsAsDraft() {
    InspectionQuotation quotation = newQuotation(1);

    assertThat(quotation.getCurrency()).isEqualTo("USD");
    assertThat(quotation.getStatus()).isEqualTo(InspectionQuotationStatus.DRAFT);
    assertThat(quotation.getVersionNumber()).isEqualTo(1);
  }

  @Test
  void rejectsNegativeAmountsAndNonPositiveVersion() {
    assertThatThrownBy(
            () ->
                new InspectionQuotation(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    0,
                    null,
                    UUID.randomUUID(),
                    "USD",
                    BigDecimal.ONE,
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    "{}",
                    "{}",
                    BigDecimal.ONE,
                    "NET 30"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quotation version must be positive");

    assertThatThrownBy(
            () ->
                new InspectionQuotation(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    null,
                    UUID.randomUUID(),
                    "USD",
                    BigDecimal.valueOf(-1),
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    "{}",
                    "{}",
                    BigDecimal.ONE,
                    "NET 30"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quotation amounts must not be negative");
  }

  @Test
  void revisionRequiresReason() {
    InspectionQuotation quotation = newQuotation(1);
    quotation.send();

    assertThatThrownBy(() -> quotation.requestRevision(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Revision reason must not be blank");

    quotation.requestRevision("Please clarify the access scope");

    assertThat(quotation.getStatus()).isEqualTo(InspectionQuotationStatus.REVISION_REQUESTED);
    assertThat(quotation.getRevisionReason()).isEqualTo("Please clarify the access scope");
  }

  @Test
  void approvalRecordsClientDecision() {
    InspectionQuotation quotation = newQuotation(1);
    quotation.send();
    UUID clientId = UUID.randomUUID();

    quotation.approve(clientId);

    assertThat(quotation.getStatus()).isEqualTo(InspectionQuotationStatus.APPROVED);
    assertThat(quotation.getDecidedByUserId()).isEqualTo(clientId);
    assertThat(quotation.getDecidedAt()).isNotNull();
  }

  @Test
  void rejectedQuotationRecordsClientDecision() {
    InspectionQuotation quotation = newQuotation(1);
    quotation.send();
    UUID clientId = UUID.randomUUID();

    quotation.reject(clientId);

    assertThat(quotation.getStatus()).isEqualTo(InspectionQuotationStatus.REJECTED);
    assertThat(quotation.getDecidedByUserId()).isEqualTo(clientId);
    assertThat(quotation.getDecidedAt()).isNotNull();
  }

  @Test
  void supersedesRevisionRequestedVersionWithoutLosingSentTime() {
    InspectionQuotation quotation = newQuotation(1);
    quotation.send();
    quotation.requestRevision("Please clarify the access scope");

    quotation.supersede();

    assertThat(quotation.getStatus()).isEqualTo(InspectionQuotationStatus.SUPERSEDED);
    assertThat(quotation.getSentAt()).isNotNull();
  }

  private InspectionQuotation newQuotation(int version) {
    return new InspectionQuotation(
        UUID.randomUUID(),
        UUID.randomUUID(),
        version,
        null,
        UUID.randomUUID(),
        " usd ",
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(110),
        "{\"items\":[]}",
        "{\"scope\":\"tower\"}",
        BigDecimal.valueOf(2),
        "NET 30");
  }
}
