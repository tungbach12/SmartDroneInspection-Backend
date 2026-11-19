package com.smartdroneinspection.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceRequestLifecycleTest {

  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID ASSET_ID = UUID.randomUUID();
  private static final UUID CREATOR_ID = UUID.randomUUID();

  @Test
  void exposesRequestDetailsAndLifecycleStatus() {
    ServiceRequest request = request();

    assertThat(request.getOwnerCustomerId()).isEqualTo(OWNER_ID);
    assertThat(request.getAssetId()).isEqualTo(ASSET_ID);
    assertThat(request.getCreatedByUserId()).isEqualTo(CREATOR_ID);
    assertThat(request.getDescription()).isEqualTo("Bridge inspection");
    assertThat(request.getRequestedFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(request.getRequestedUntil()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(request.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
    assertThat(request.getWindowStart()).isEqualTo(LocalTime.of(8, 0));
    assertThat(request.getWindowEnd()).isEqualTo(LocalTime.of(17, 0));
    assertThat(request.getRequestType()).isEqualTo(ServiceRequest.RequestType.ONE_TIME);
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.OPEN);
    assertThat(request.getRecurrenceUnit()).isNull();
    assertThat(request.getRecurrenceInterval()).isNull();
    assertThat(request.getOccurrenceCount()).isNull();
    assertThat(request.getRecurrenceEndDate()).isNull();
    assertThat(request.getRowVersion()).isZero();
    assertThat(request.getSourceInspectionId()).isNull();
    assertThat(request.hasSignedContract()).isFalse();
    assertThat(request.getContractFileName()).isNull();
    assertThat(request.getContractContentType()).isNull();
  }

  @Test
  void claimsReassignsAndUnassignsManager() {
    ServiceRequest request = request();
    UUID managerId = UUID.randomUUID();
    Instant now = Instant.now();

    request.claimByManager(managerId, now);
    assertThat(request.getAssignedManagerId()).isEqualTo(managerId);
    assertThat(request.getManagerAssignedByUserId()).isEqualTo(managerId);
    assertThat(request.getManagerAssignedAt()).isEqualTo(now);
    assertThatThrownBy(() -> request.claimByManager(managerId, now))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> request.claimByManager(null, now))
        .isInstanceOf(IllegalStateException.class);

    UUID reassigner = UUID.randomUUID();
    request.reassignManager(UUID.randomUUID(), reassigner, now.plusSeconds(1));
    assertThat(request.getManagerAssignedByUserId()).isEqualTo(reassigner);
    assertThatThrownBy(() -> request.reassignManager(null, reassigner, now))
        .isInstanceOf(IllegalStateException.class);

    request.unassignManager(reassigner, now.plusSeconds(2));
    assertThat(request.getAssignedManagerId()).isNull();
    assertThatThrownBy(() -> request.unassignManager(reassigner, now))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void quotesAcceptsAndSignsContract() {
    ServiceRequest request = request();
    UUID managerId = UUID.randomUUID();
    request.markQuoted();
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.QUOTED);

    ServiceRequestQuote quote = quote(request.getId());
    request.acceptQuote(quote, OWNER_ID);
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACCEPTED);
    assertThat(request.getAcceptedQuoteId()).isEqualTo(quote.getId());
    assertThat(request.getAcceptedByUserId()).isEqualTo(OWNER_ID);
    assertThat(request.getAcceptedAt()).isNotNull();
    assertThat(request.getAcceptedTermsSnapshot())
        .containsEntry("quoteId", quote.getId())
        .containsEntry("pricePerOccurrenceVnd", new BigDecimal("100.00"));

    request.recordSignedContract("contracts/signed.pdf");
    assertThat(request.hasSignedContract()).isTrue();
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.CONTRACT_SIGNED);
    assertThat(request.getContractFileName()).isEqualTo("signed.pdf");
    assertThat(request.getContractContentType()).isEqualTo("application/pdf");
    assertThat(request.getContractObjectKey()).isEqualTo("contracts/signed.pdf");
    assertThat(request.getContractSizeBytes()).isNull();
    assertThat(request.getContractUploadedAt()).isNotNull();

    request.markAssigned();
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ASSIGNED);
    request.reopenForAssignment();
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.CONTRACT_SIGNED);
  }

  @Test
  void derivesContractContentTypeFromFileName() {
    ServiceRequest request = request();
    request.markQuoted();
    request.acceptQuote(quote(request.getId()), OWNER_ID);

    request.recordSignedContract("contracts/signed.docx");
    assertThat(request.getContractFileName()).isEqualTo("signed.docx");
    assertThat(request.getContractContentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    request.recordSignedContract("contracts/signed.png");
    assertThat(request.getContractContentType()).isEqualTo("image/png");

    request.recordSignedContract("contracts/signed.jpg");
    assertThat(request.getContractContentType()).isEqualTo("image/jpeg");
  }

  @Test
  void rejectsInvalidQuoteAndContractTransitions() {
    ServiceRequest request = request();
    assertThatThrownBy(() -> request.recordSignedContract("contracts/signed.pdf"))
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> request.acceptQuote(null, OWNER_ID))
        .isInstanceOf(IllegalStateException.class);

    request.markQuoted();
    assertThatThrownBy(() -> request.acceptQuote(quote(UUID.randomUUID()), OWNER_ID))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> request.acceptQuote(quote(request.getId()), null))
        .isInstanceOf(IllegalStateException.class);

    request.acceptQuote(quote(request.getId()), OWNER_ID);
    assertThatThrownBy(() -> request.markQuoted()).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> request.recordSignedContract(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                request.recordSignedContract(
                    "contracts/signed.pdf", null, "application/pdf", 1, "sha", OWNER_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void handlesFlatContractKeysAndIdempotentTransitions() {
    ServiceRequest request = request();
    request.markQuoted();
    request.markQuoted();
    request.acceptQuote(quote(request.getId()), OWNER_ID);

    request.recordSignedContract("signed.jpeg");
    assertThat(request.getContractFileName()).isEqualTo("signed.jpeg");
    assertThat(request.getContractContentType()).isEqualTo("image/jpeg");

    request.recordSignedContract(
        "contracts", "signed.pdf", "application/pdf", 100, "sha", OWNER_ID);
    assertThat(request.getContractObjectKey()).isEqualTo("contracts/signed.pdf");

    request.markAssigned();
    request.markAssigned();
    request.reopenForAssignment();
    request.reopenForAssignment();
    assertThat(request.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.CONTRACT_SIGNED);
  }

  @Test
  void derivesJpegAndDirectoryContractNames() {
    ServiceRequest request = request();
    request.markQuoted();
    request.acceptQuote(quote(request.getId()), OWNER_ID);

    request.recordSignedContract("contracts/");
    assertThat(request.getContractFileName()).isEqualTo("contracts/");

    request.recordSignedContract("contracts/image.jpeg");
    assertThat(request.getContractFileName()).isEqualTo("image.jpeg");
    assertThat(request.getContractContentType()).isEqualTo("image/jpeg");
  }

  @Test
  void rejectsInvalidRequestConstructionBranches() {
    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    null,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owner");

    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    "Invalid/Zone",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timezone is invalid");

    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(17, 0),
                    LocalTime.of(8, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("window is invalid");
  }

  @Test
  void rejectsMoreInvalidRequestConstructionBranches() {
    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    null,
                    LocalDate.of(2027, 1, 1),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date range");

    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Request type is required");

    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 2),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one requested date");

    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "x".repeat(4001),
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4000 characters");

    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    OWNER_ID,
                    ASSET_ID,
                    CREATOR_ID,
                    "Bridge",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    3,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot include recurrence limits");
  }

  private static ServiceRequest request() {
    ServiceRequest request =
        new ServiceRequest(
            OWNER_ID,
            ASSET_ID,
            CREATOR_ID,
            "Bridge inspection",
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 1),
            "Asia/Ho_Chi_Minh",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            ServiceRequest.RequestType.ONE_TIME,
            null,
            null,
            null,
            null);
    ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
    return request;
  }

  private static ServiceRequestQuote quote(UUID requestId) {
    ServiceRequestQuote quote =
        new ServiceRequestQuote(
            requestId,
            "Scope",
            Instant.parse("2027-02-01T09:00:00Z"),
            Instant.parse("2027-02-01T11:00:00Z"),
            new BigDecimal("100.00"),
            UUID.randomUUID());
    ReflectionTestUtils.setField(quote, "id", UUID.randomUUID());
    return quote;
  }
}
