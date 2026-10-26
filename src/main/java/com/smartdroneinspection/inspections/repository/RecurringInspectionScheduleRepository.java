package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.RecurringInspectionSchedule;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface RecurringInspectionScheduleRepository
    extends JpaRepository<RecurringInspectionSchedule, UUID> {

  Optional<RecurringInspectionSchedule> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(
      value = {
        @QueryHint(name = "jakarta.persistence.cache.storeMode", value = "REFRESH"),
        @QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "BYPASS")
      })
  @Query(
      "select s from RecurringInspectionSchedule s where s.id = :id and s.organizationId = :organizationId")
  Optional<RecurringInspectionSchedule> findWithLockByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  Page<RecurringInspectionSchedule> findByOrganizationId(UUID organizationId, Pageable pageable);

  Page<RecurringInspectionSchedule> findByOrganizationIdAndStatus(
      UUID organizationId, String status, Pageable pageable);

  Page<RecurringInspectionSchedule> findByOrganizationIdAndAssetId(
      UUID organizationId, UUID assetId, Pageable pageable);

  Page<RecurringInspectionSchedule> findByOrganizationIdAndAssetIdAndStatus(
      UUID organizationId, UUID assetId, String status, Pageable pageable);

  @Query(
      "select s from RecurringInspectionSchedule s where s.status = 'ACTIVE' order by s.createdAt asc")
  List<RecurringInspectionSchedule> findActiveSchedules(Pageable pageable);

  long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
