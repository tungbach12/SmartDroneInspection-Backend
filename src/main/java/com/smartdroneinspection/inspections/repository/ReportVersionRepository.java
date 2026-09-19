package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.ReportVersion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {}
