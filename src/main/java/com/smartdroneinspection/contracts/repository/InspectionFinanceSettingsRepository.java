package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InspectionFinanceSettingsRepository
    extends JpaRepository<InspectionFinanceSettings, UUID> {

  @Query("select s from InspectionFinanceSettings s order by s.updatedAt desc")
  Optional<InspectionFinanceSettings> findCurrentSettings();
}
