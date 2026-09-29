package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.ScheduleProposal;
import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleProposalRepository extends JpaRepository<ScheduleProposal, UUID> {
  List<ScheduleProposal> findByAssetIdOrderByCreatedAtAsc(UUID assetId);

  List<ScheduleProposal> findByAssetIdAndStatus(UUID assetId, ScheduleProposalStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select proposal from ScheduleProposal proposal where proposal.id = :id")
  Optional<ScheduleProposal> findWithLockById(@Param("id") UUID id);
}
