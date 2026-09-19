package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFindingCandidateRepository extends JpaRepository<AiFindingCandidate, UUID> {}
