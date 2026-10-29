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

  List<ServiceRequest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<ServiceRequest> findAllByOrderByCreatedAtDesc();

  Optional<ServiceRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select request from ServiceRequest request where request.id = :id")
  Optional<ServiceRequest> findWithLockById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select request from ServiceRequest request where request.id = :id "
          + "and request.organizationId = :organizationId")
  Optional<ServiceRequest> findWithLockByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
