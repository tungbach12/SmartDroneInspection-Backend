package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

  List<ServiceRequest> findByOwnerCustomerIdOrderByCreatedAtDesc(UUID ownerCustomerId);

  List<ServiceRequest> findByAssignedManagerIdAndStatusNotOrderByCreatedAtDesc(
      UUID assignedManagerId, ServiceRequest.ServiceRequestStatus excludedStatus);

  @Query(
      """
      select count(request) > 0 from ServiceRequest request
      where request.ownerCustomerId = :ownerCustomerId and request.assetId = :assetId
        and (:anySource = true or request.sourceInspectionId = :sourceInspectionId)
        and request.status <> CANCELLED
        and (request.status not in (ASSIGNED, CONTRACT_SIGNED)
          or not exists (select 1 from ServiceRequestOccurrence occurrence
                         where occurrence.requestId = request.id)
          or exists (select 1 from ServiceRequestOccurrence occurrence left join occurrence.invoice invoice
                     where occurrence.requestId = request.id
                       and occurrence.status <> CANCELLED and invoice.reportAcceptedAt is null))
      """)
  boolean existsActiveRequestForAsset(
      @Param("ownerCustomerId") UUID ownerCustomerId,
      @Param("assetId") UUID assetId,
      @Param("anySource") boolean anySource,
      @Param("sourceInspectionId") UUID sourceInspectionId);

  List<ServiceRequest> findAllByOrderByCreatedAtDesc();

  @Query(
      "select request from ServiceRequest request where request.assignedManagerId is null "
          + "or request.assignedManagerId = :managerId order by request.createdAt desc")
  List<ServiceRequest> findManagerQueue(@Param("managerId") UUID managerId);

  Optional<ServiceRequest> findByIdAndOwnerCustomerId(UUID id, UUID ownerCustomerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select request from ServiceRequest request where request.id = :id")
  Optional<ServiceRequest> findWithLockById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select request from ServiceRequest request where request.id = :id "
          + "and request.ownerCustomerId = :ownerCustomerId")
  Optional<ServiceRequest> findWithLockByIdAndOwnerCustomerId(
      @Param("id") UUID id, @Param("ownerCustomerId") UUID ownerCustomerId);
}
