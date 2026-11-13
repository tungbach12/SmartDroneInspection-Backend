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

  List<ServiceRequest> findAllByOrderByCreatedAtDesc();

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
