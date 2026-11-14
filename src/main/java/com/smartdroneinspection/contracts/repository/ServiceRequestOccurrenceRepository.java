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

  @Query(
      value =
          "select occurrence from ServiceRequestOccurrence occurrence "
              + "join ServiceRequest request on request.id = occurrence.requestId "
              + "where request.ownerCustomerId = :ownerId "
              + "and occurrence.payment.paymentStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus.PAID "
              + "order by occurrence.payment.paymentConfirmedAt desc, occurrence.id desc",
      countQuery =
          "select count(occurrence) from ServiceRequestOccurrence occurrence "
              + "join ServiceRequest request on request.id = occurrence.requestId "
              + "where request.ownerCustomerId = :ownerId "
              + "and occurrence.payment.paymentStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus.PAID")
  org.springframework.data.domain.Page<ServiceRequestOccurrence> pagePaidByCustomer(
      @Param("ownerId") UUID ownerId, org.springframework.data.domain.Pageable pageable);

  @Query(
      value =
          "select occurrence from ServiceRequestOccurrence occurrence "
              + "where occurrence.inspectorId = :inspectorId "
              + "and occurrence.payout.payoutStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus.PAID "
              + "order by occurrence.payout.payoutRecordedAt desc, occurrence.id desc",
      countQuery =
          "select count(occurrence) from ServiceRequestOccurrence occurrence "
              + "where occurrence.inspectorId = :inspectorId "
              + "and occurrence.payout.payoutStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus.PAID")
  org.springframework.data.domain.Page<ServiceRequestOccurrence> pagePaidByInspector(
      @Param("inspectorId") UUID inspectorId, org.springframework.data.domain.Pageable pageable);

  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence "
          + "join ServiceRequest request on request.id = occurrence.requestId "
          + "where request.ownerCustomerId = :ownerId and occurrence.id = :occurrenceId "
          + "and occurrence.payment.paymentStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus.PAID")
  Optional<ServiceRequestOccurrence> findPaidByCustomer(
      @Param("ownerId") UUID ownerId, @Param("occurrenceId") UUID occurrenceId);

  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence "
          + "where occurrence.inspectorId = :inspectorId and occurrence.id = :occurrenceId "
          + "and occurrence.payout.payoutStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus.PAID")
  Optional<ServiceRequestOccurrence> findPaidByInspector(
      @Param("inspectorId") UUID inspectorId, @Param("occurrenceId") UUID occurrenceId);

  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence "
          + "join ServiceRequest request on request.id = occurrence.requestId "
          + "where request.ownerCustomerId = :customerId "
          + "and occurrence.payment.paymentStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus.PAID "
          + "order by occurrence.payment.paymentConfirmedAt desc, occurrence.id desc")
  org.springframework.data.domain.Page<ServiceRequestOccurrence> findPaidHistoryForCustomer(
      @Param("customerId") UUID customerId, org.springframework.data.domain.Pageable pageable);

  @Query(
      "select occurrence from ServiceRequestOccurrence occurrence "
          + "where occurrence.inspectorId = :inspectorId "
          + "and occurrence.payout.payoutStatus = com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus.PAID "
          + "order by occurrence.payout.payoutRecordedAt desc, occurrence.id desc")
  org.springframework.data.domain.Page<ServiceRequestOccurrence> findPaidPayoutHistoryForInspector(
      @Param("inspectorId") UUID inspectorId, org.springframework.data.domain.Pageable pageable);

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
}
