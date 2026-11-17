package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestOccurrenceRepository
    extends JpaRepository<ServiceRequestOccurrence, UUID> {

  List<ServiceRequestOccurrence> findByRequestIdOrderByOccurrenceIndexAsc(UUID requestId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select occurrence from ServiceRequestOccurrence occurrence where occurrence.id = :id")
  Optional<ServiceRequestOccurrence> findWithLockById(@Param("id") UUID id);

  Optional<ServiceRequestOccurrence> findByInspectionId(UUID inspectionId);

  List<ServiceRequestOccurrence> findByInspectionIdIn(java.util.Collection<UUID> inspectionIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence where occurrence.inspectionId = :inspectionId")
  Optional<ServiceRequestOccurrence> findWithLockByInspectionId(
      @Param("inspectionId") UUID inspectionId);

  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence join occurrence.payment payment where payment.payosOrderCode = :payosOrderCode")
  Optional<ServiceRequestOccurrence> findByPayosOrderCode(
      @Param("payosOrderCode") Long payosOrderCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence join occurrence.payment payment where payment.payosOrderCode = :orderCode")
  Optional<ServiceRequestOccurrence> findWithLockByPayosOrderCode(
      @Param("orderCode") Long orderCode);

  @Query(
      "select count(occurrence) > 0 from ServiceRequestOccurrence occurrence "
          + "where (occurrence.inspectorId = :inspectorId or occurrence.droneId = :droneId) "
          + "and occurrence.status = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED "
          + "and occurrence.plannedStartAt < :end and occurrence.plannedEndAt > :start")
  boolean hasScheduleConflict(
      @Param("inspectorId") UUID inspectorId,
      @Param("droneId") UUID droneId,
      @Param("start") java.time.Instant start,
      @Param("end") java.time.Instant end);

  @Query(
      "select count(occurrence) > 0 from ServiceRequestOccurrence occurrence "
          + "where (occurrence.inspectionId is null or occurrence.inspectionId <> :excludeInspectionId) "
          + "and (occurrence.inspectorId = :inspectorId or occurrence.droneId = :droneId) "
          + "and occurrence.status = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED "
          + "and occurrence.plannedStartAt < :end and occurrence.plannedEndAt > :start")
  boolean hasScheduleConflictExcludingInspection(
      @Param("inspectorId") UUID inspectorId,
      @Param("droneId") UUID droneId,
      @Param("start") java.time.Instant start,
      @Param("end") java.time.Instant end,
      @Param("excludeInspectionId") UUID excludeInspectionId);

  /**
   * Unpaid occurrences whose customer payment deadline has passed and that have not yet been
   * notified as overdue. Drives the daily overdue-warning job.
   */
  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence join occurrence.payment payment "
          + "where payment.paymentStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus.PAYMENT_DUE "
          + "and payment.paymentDueAt is not null "
          + "and payment.paymentDueAt < :now "
          + "and payment.paymentOverdueNotifiedAt is null")
  List<ServiceRequestOccurrence> findOverduePayments(@Param("now") java.time.Instant now);
}
