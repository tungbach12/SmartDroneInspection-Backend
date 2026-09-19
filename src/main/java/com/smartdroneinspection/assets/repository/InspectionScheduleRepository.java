package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionScheduleRepository extends JpaRepository<InspectionSchedule, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select schedule
      from InspectionSchedule schedule
      where schedule.status = :status
        and schedule.nextDueAt <= :now
      order by schedule.nextDueAt, schedule.id
      """)
  List<InspectionSchedule> findDueForUpdate(
      @Param("status") InspectionScheduleStatus status,
      @Param("now") Instant now,
      Pageable pageable);

  List<InspectionSchedule> findByAssetIdOrderByNextDueAt(UUID assetId);
}
