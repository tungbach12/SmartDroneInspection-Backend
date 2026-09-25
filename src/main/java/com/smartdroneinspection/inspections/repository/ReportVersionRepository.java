package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.ReportVersion;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {

  List<ReportVersion> findByReportIdOrderByVersionNumberDesc(UUID reportId);

  Optional<ReportVersion> findFirstByReportIdAndStatusInOrderByVersionNumberDesc(
      UUID reportId, Collection<ReportStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select version from ReportVersion version where version.id = :versionId")
  Optional<ReportVersion> findForUpdateById(@Param("versionId") UUID versionId);
}
