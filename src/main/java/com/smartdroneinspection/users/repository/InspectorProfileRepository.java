package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.InspectorProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectorProfileRepository extends JpaRepository<InspectorProfile, UUID> {

  boolean existsByPilotLicenseClassIgnoreCase(String pilotLicenseClass);
}
