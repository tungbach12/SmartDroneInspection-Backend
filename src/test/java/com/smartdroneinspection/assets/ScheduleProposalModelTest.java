package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleProposalModelTest {

  private ScheduleProposal proposal() {
    return ScheduleProposal.generate(UUID.randomUUID(), UUID.randomUUID(), "MONTH", 3);
  }

  @Test
  void generatedProposalMustBeManagedBeforeClientSelection() {
    ScheduleProposal p = proposal();
    assertThat(p.getStatus()).isEqualTo(ScheduleProposalStatus.GENERATED);

    assertThatThrownBy(() -> p.clientSelect(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void managerApprovalAllowsExactlyOneClientSelection() {
    ScheduleProposal p = proposal();
    UUID client = UUID.randomUUID();
    p.managerApprove("Quarterly fits this asset", UUID.randomUUID());

    p.clientSelect(client);
    assertThat(p.getStatus()).isEqualTo(ScheduleProposalStatus.CLIENT_SELECTED);
    assertThat(p.getSelectedByUserId()).isEqualTo(client);

    assertThatThrownBy(() -> p.clientSelect(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void adjustAndRejectAreOnlyValidBeforeClientSelection() {
    ScheduleProposal p = proposal();
    p.managerApprove(null, UUID.randomUUID());
    p.clientSelect(UUID.randomUUID());

    assertThatThrownBy(() -> p.managerAdjust("WEEK", 1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> p.managerReject("changed my mind", UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void supersedeOnlyAppliesToManagerApprovedProposals() {
    ScheduleProposal p = proposal();
    p.managerApprove(null, UUID.randomUUID());
    p.supersede();
    assertThat(p.getStatus()).isEqualTo(ScheduleProposalStatus.SUPERSEDED);

    assertThatThrownBy(() -> p.clientSelect(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }
}
