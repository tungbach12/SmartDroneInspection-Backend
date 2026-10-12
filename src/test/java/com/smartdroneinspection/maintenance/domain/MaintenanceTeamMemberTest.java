package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.TeamMemberRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MF4-03/04: team assignment history. Replacing a lead or report author closes the previous row
 * instead of overwriting it, so historical work logs keep their original author.
 */
class MaintenanceTeamMemberTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID ENGINEER = UUID.randomUUID();
  private static final UUID ASSIGNER = UUID.randomUUID();

  private static MaintenanceTeamMember member(TeamMemberRole role) {
    return new MaintenanceTeamMember(
        WORK_ORDER, ENGINEER, role, Instant.now(), ASSIGNER, "Initial");
  }

  @Test
  void createsActiveAssignment() {
    MaintenanceTeamMember m = member(TeamMemberRole.LEAD);

    assertThat(m.isActive()).isTrue();
    assertThat(m.getMemberRole()).isEqualTo(TeamMemberRole.LEAD);
    assertThat(m.getEffectiveUntil()).isNull();
  }

  @Test
  void requiresEngineerRoleAndAssigner() {
    Instant now = Instant.now();
    assertThatThrownBy(
            () ->
                new MaintenanceTeamMember(
                    WORK_ORDER, null, TeamMemberRole.LEAD, now, ASSIGNER, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MaintenanceTeamMember(WORK_ORDER, ENGINEER, null, now, ASSIGNER, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceTeamMember(
                    WORK_ORDER, ENGINEER, TeamMemberRole.LEAD, now, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void coversItsEffectiveWindowOnly() {
    Instant from = Instant.now().minusSeconds(3600);
    MaintenanceTeamMember m =
        new MaintenanceTeamMember(
            WORK_ORDER, ENGINEER, TeamMemberRole.MEMBER, from, ASSIGNER, null);

    assertThat(m.covers(Instant.now())).isTrue();
    assertThat(m.covers(from.minusSeconds(1))).isFalse();
  }

  @Test
  void closesWithHandoverAndReason() {
    Instant from = Instant.now().minusSeconds(60);
    MaintenanceTeamMember m =
        new MaintenanceTeamMember(WORK_ORDER, ENGINEER, TeamMemberRole.LEAD, from, ASSIGNER, null);
    Instant until = Instant.now();

    m.closeWithHandover(until, "Replaced by another engineer");

    assertThat(m.isActive()).isFalse();
    assertThat(m.covers(until)).isFalse();
  }

  @Test
  void handoverRequiresReasonAndForwardDate() {
    MaintenanceTeamMember m = member(TeamMemberRole.LEAD);
    Instant from = m.getEffectiveFrom();

    assertThatThrownBy(() -> m.closeWithHandover(Instant.now().plusSeconds(60), "  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> m.closeWithHandover(from, "Replaced"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> m.closeWithHandover(from.minusSeconds(60), "Replaced"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsClosingAnAlreadyClosedAssignment() {
    MaintenanceTeamMember m = member(TeamMemberRole.LEAD);
    m.closeWithHandover(Instant.now().plusSeconds(60), "Replaced");

    assertThatThrownBy(() -> m.closeWithHandover(Instant.now().plusSeconds(120), "Again"))
        .isInstanceOf(IllegalStateException.class);
  }
}
