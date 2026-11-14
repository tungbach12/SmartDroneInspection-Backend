package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.DroneDocumentConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DroneDocumentConfigRepository extends JpaRepository<DroneDocumentConfig, UUID> {

  List<DroneDocumentConfig> findAllByOrderByDisplayOrderAscCreatedAtAsc();

  List<DroneDocumentConfig> findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

  Optional<DroneDocumentConfig> findByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCase(String code);
}
