package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.PartnerDrone;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractPartnerDroneRepository extends JpaRepository<PartnerDrone, UUID> {

  @Query("select partner from PartnerDrone partner where partner.status = 'ACTIVE'")
  List<PartnerDrone> findActive();

  @Query(
      "select partner from PartnerDrone partner where partner.droneId = :id "
          + "and partner.status = 'ACTIVE'")
  Optional<PartnerDrone> findActiveByDroneId(@Param("id") UUID droneId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select partner from PartnerDrone partner where partner.droneId = :id")
  Optional<PartnerDrone> findWithLockByDroneId(@Param("id") UUID droneId);
}
