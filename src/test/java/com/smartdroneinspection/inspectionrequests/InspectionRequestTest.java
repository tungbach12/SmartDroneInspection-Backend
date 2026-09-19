package com.smartdroneinspection.inspectionrequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionRequestPriority;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionRequestTest {

  @Test
  void periodicRequiresScheduleAndDueCycle() {
    assertThatThrownBy(
            () ->
                InspectionRequest.periodic(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    LocalDate.of(2026, 9, 1),
                    UUID.randomUUID(),
                    "Tower inspection",
                    InspectionRequestPriority.NORMAL,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Periodic requests require a schedule");

    assertThatThrownBy(
            () ->
                InspectionRequest.periodic(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    "Tower inspection",
                    InspectionRequestPriority.NORMAL,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Periodic requests require a due cycle");
  }

  @Test
  void adHocAlwaysStoresNullScheduleAndDueCycle() {
    InspectionRequest request =
        InspectionRequest.adHoc(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Emergency inspection",
            InspectionRequestPriority.HIGH,
            Instant.now(),
            "Call site security before arrival",
            "Site contact",
            "+84000000000",
            "contact@example.com",
            null);

    assertThat(request.getRequestType().name()).isEqualTo("AD_HOC");
    assertThat(request.getScheduleId()).isNull();
    assertThat(request.getDueCycle()).isNull();
    assertThat(request.getStatus()).isEqualTo(InspectionRequestStatus.DRAFT);
  }

  @Test
  void submitChangesOnlyAValidDraftAndRecordsSubmittedAt() {
    InspectionRequest request = newAdHocRequest();

    request.submit();

    assertThat(request.getStatus()).isEqualTo(InspectionRequestStatus.SUBMITTED);
    assertThat(request.getSubmittedAt()).isNotNull();
    Instant submittedAt = request.getSubmittedAt();

    assertThatThrownBy(request::submit)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only draft requests can be submitted");
    assertThat(request.getSubmittedAt()).isEqualTo(submittedAt);
  }

  @Test
  void rejectsInvalidAttachmentMetadata() {
    InspectionRequest request = newAdHocRequest();

    assertThatThrownBy(
            () ->
                request.addAttachment(
                    UUID.randomUUID(),
                    "evidence.jpg",
                    "image/jpeg",
                    0,
                    "a".repeat(64),
                    "key",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Attachment size must be positive");

    assertThatThrownBy(
            () ->
                request.addAttachment(
                    UUID.randomUUID(),
                    "evidence.jpg",
                    "image/jpeg",
                    1,
                    "A".repeat(64),
                    "key",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Checksum must be a 64-character lowercase hexadecimal SHA-256 value");

    assertThatThrownBy(
            () ->
                request.addAttachment(
                    UUID.randomUUID(), "evidence.jpg", "image/jpeg", 1, "a".repeat(64), " ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Object key must not be blank");

    assertThatThrownBy(
            () ->
                request.addAttachment(
                    UUID.randomUUID(), " ", "image/jpeg", 1, "a".repeat(64), "key", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("File name must not be blank");

    assertThatThrownBy(
            () ->
                request.addAttachment(
                    UUID.randomUUID(), "evidence.jpg", " ", 1, "a".repeat(64), "key", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Content type must not be blank");
  }

  @Test
  void cancelRejectsOrderProgressStates() {
    InspectionRequest request = newAdHocRequest();
    request.submit();
    request.markQuoted();
    request.markAwaitingClientApproval();
    request.markOrderConfirmed();

    assertThatThrownBy(request::cancel)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Order-progress requests cannot be cancelled");
  }

  private InspectionRequest newAdHocRequest() {
    return InspectionRequest.adHoc(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Tower inspection",
        InspectionRequestPriority.NORMAL,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
