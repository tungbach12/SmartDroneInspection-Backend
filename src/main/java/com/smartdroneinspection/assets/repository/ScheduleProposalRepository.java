package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleProposalRepository extends JpaRepository<ScheduleProposal, UUID> {
  List<ScheduleProposal> findByAssetIdOrderByCreatedAtAsc(UUID assetId);

  List<ScheduleProposal> findByAssetIdAndStatus(UUID assetId, ScheduleProposalStatus status);
}
