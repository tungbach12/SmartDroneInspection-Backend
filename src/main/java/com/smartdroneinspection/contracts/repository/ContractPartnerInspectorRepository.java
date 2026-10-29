package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.PartnerInspector;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractPartnerInspectorRepository extends JpaRepository<PartnerInspector, UUID> {

  @Query("select partner from PartnerInspector partner where partner.status = 'ACTIVE'")
  List<PartnerInspector> findActive();

  @Query(
      "select partner from PartnerInspector partner where partner.inspectorUserId = :id "
          + "and partner.status = 'ACTIVE'")
  Optional<PartnerInspector> findActiveByInspectorId(@Param("id") UUID inspectorId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select partner from PartnerInspector partner where partner.inspectorUserId = :id")
  Optional<PartnerInspector> findWithLockByInspectorId(@Param("id") UUID inspectorId);
}
