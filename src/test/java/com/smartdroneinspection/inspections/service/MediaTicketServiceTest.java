package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.api.dto.response.MediaTicketResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MediaTicketServiceTest {

  @Test
  void issuesAndVerifiesScopedTicket() {
    EvidenceUploadProperties props = new EvidenceUploadProperties();
    props.setMediaTicketTtl(Duration.ofMinutes(5));
    props.setMediaTicketSecret("test-secret");
    MediaTicketService service = new MediaTicketService(props, "fallback-secret");

    UUID actorId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();

    MediaTicketResponse issued = service.issueTicket(actorId, inspectionId, evidenceId, "thumb");

    assertThat(issued.streamUrl())
        .contains(
            "/api/v1/inspections/" + inspectionId + "/evidence/" + evidenceId + "/stream?ticket=");

    MediaTicketService.VerifiedTicket verified =
        service.verifyTicket(issued.ticket(), inspectionId, evidenceId);
    assertThat(verified.actorId()).isEqualTo(actorId);
    assertThat(verified.inspectionId()).isEqualTo(inspectionId);
    assertThat(verified.evidenceId()).isEqualTo(evidenceId);
    assertThat(verified.variant()).isEqualTo("thumb");
  }

  @Test
  void rejectsTamperedWrongScopeAndExpiredTickets() {
    EvidenceUploadProperties props = new EvidenceUploadProperties();
    props.setMediaTicketTtl(Duration.ofMinutes(5));
    props.setMediaTicketSecret("test-secret");
    MediaTicketService service = new MediaTicketService(props, "");

    UUID actorId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    MediaTicketResponse issued = service.issueTicket(actorId, inspectionId, evidenceId, "primary");

    String tampered = issued.ticket().substring(0, issued.ticket().length() - 2) + "AA";
    assertThatThrownBy(() -> service.verifyTicket(tampered, inspectionId, evidenceId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).status()).isEqualTo(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> service.verifyTicket(issued.ticket(), UUID.randomUUID(), evidenceId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).status()).isEqualTo(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> service.issueTicket(actorId, inspectionId, evidenceId, "raw"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).status()).isEqualTo(HttpStatus.BAD_REQUEST));

    EvidenceUploadProperties expiredProps = new EvidenceUploadProperties();
    expiredProps.setMediaTicketTtl(Duration.ofSeconds(-5));
    expiredProps.setMediaTicketSecret("test-secret");
    MediaTicketService expiredService = new MediaTicketService(expiredProps, "");
    MediaTicketResponse expired =
        expiredService.issueTicket(actorId, inspectionId, evidenceId, "primary");

    assertThatThrownBy(
            () -> expiredService.verifyTicket(expired.ticket(), inspectionId, evidenceId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("expired");
  }
}
