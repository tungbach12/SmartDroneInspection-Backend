package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.PilotLicenseClass;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotLicenseClassRepository extends JpaRepository<PilotLicenseClass, UUID> {

  List<PilotLicenseClass> findAllByOrderByNameAsc();

  List<PilotLicenseClass> findAllByActiveTrueOrderByNameAsc();

  Optional<PilotLicenseClass> findByNameIgnoreCaseAndActiveTrue(String name);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
