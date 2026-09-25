package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiFindingCandidateRepository extends JpaRepository<AiFindingCandidate, UUID> {

  List<AiFindingCandidate> findByEvidenceIdInOrderByCreatedAtDesc(Collection<UUID> evidenceIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select candidate from AiFindingCandidate candidate where candidate.id = :id")
  Optional<AiFindingCandidate> findForUpdateById(@Param("id") UUID id);
}
