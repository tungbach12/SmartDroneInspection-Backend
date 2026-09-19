package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerifiedFindingRepository extends JpaRepository<VerifiedFinding, UUID> {}
