package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.DroneFeatureConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DroneFeatureConfigRepository extends JpaRepository<DroneFeatureConfig, UUID> {

  List<DroneFeatureConfig> findAllByOrderByDisplayOrderAscCreatedAtAsc();

  List<DroneFeatureConfig> findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

  boolean existsByCodeIgnoreCase(String code);
}
