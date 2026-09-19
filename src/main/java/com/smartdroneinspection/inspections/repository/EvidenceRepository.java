package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Evidence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {}
