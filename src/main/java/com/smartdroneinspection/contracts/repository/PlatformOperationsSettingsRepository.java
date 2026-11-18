package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.PlatformOperationsSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformOperationsSettingsRepository
    extends JpaRepository<PlatformOperationsSettings, UUID> {

  Optional<PlatformOperationsSettings> findFirstByOrderByUpdatedAtDesc();
}
