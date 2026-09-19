package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.InspectionAssignment;
import com.smartdroneinspection.inspections.domain.InspectionAssignmentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionAssignmentTest {

  @Test
  void acceptsPendingAssignmentAndRecordsResponseTime() {
    InspectionAssignment assignment = newAssignment();

    assignment.accept();

    assertThat(assignment.getStatus()).isEqualTo(InspectionAssignmentStatus.ACCEPTED);
    assertThat(assignment.getRespondedAt()).isNotNull();
    assertThatThrownBy(assignment::accept)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only pending assignments can be accepted");
  }

  @Test
  void rejectsAssignmentOnlyWithNonBlankReason() {
    InspectionAssignment assignment = newAssignment();

    assertThatThrownBy(() -> assignment.reject(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Rejection reason must not be blank");

    assignment.reject("Inspector is unavailable");

    assertThat(assignment.getStatus()).isEqualTo(InspectionAssignmentStatus.REJECTED);
    assertThat(assignment.getRejectionReason()).isEqualTo("Inspector is unavailable");
    assertThat(assignment.getRespondedAt()).isNotNull();
    assertThatThrownBy(() -> assignment.reject("Another reason"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only pending assignments can be rejected");
  }

  @Test
  void cancelsPendingOrAcceptedAssignment() {
    InspectionAssignment pending = newAssignment();
    pending.cancel();
    assertThat(pending.getStatus()).isEqualTo(InspectionAssignmentStatus.CANCELLED);

    InspectionAssignment accepted = newAssignment();
    accepted.accept();
    accepted.cancel();
    assertThat(accepted.getStatus()).isEqualTo(InspectionAssignmentStatus.CANCELLED);
  }

  private InspectionAssignment newAssignment() {
    return new InspectionAssignment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now().plusSeconds(3600),
        "Bring site access badge");
  }
}
