package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.DroneMissionPlan;
import com.smartdroneinspection.inspections.domain.enums.MissionPlanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DroneMissionPlanRepository extends JpaRepository<DroneMissionPlan, UUID> {

  Optional<DroneMissionPlan> findByIdAndProviderId(UUID id, UUID providerId);

  List<DroneMissionPlan> findByServiceOrderIdOrderByVersionNumberDesc(UUID serviceOrderId);

  List<DroneMissionPlan> findByServiceOrderIdAndProviderIdOrderByVersionNumberDesc(
      UUID serviceOrderId, UUID providerId);

  Optional<DroneMissionPlan> findFirstByServiceOrderIdAndStatusOrderByVersionNumberDesc(
      UUID serviceOrderId, MissionPlanStatus status);
}
