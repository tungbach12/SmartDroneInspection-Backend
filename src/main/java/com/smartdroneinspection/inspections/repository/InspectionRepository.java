package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Inspection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  Optional<Inspection> findByAcceptedAssignmentId(UUID acceptedAssignmentId);

  Optional<Inspection> findByIdAndAuthorUserId(UUID inspectionId, UUID authorUserId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select inspection from Inspection inspection "
          + "where inspection.id = :inspectionId and inspection.authorUserId = :authorUserId")
  Optional<Inspection> findForUpdateByIdAndAuthorUserId(
      @Param("inspectionId") UUID inspectionId, @Param("authorUserId") UUID authorUserId);
}
