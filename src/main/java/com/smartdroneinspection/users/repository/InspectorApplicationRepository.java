package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.InspectorApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectorApplicationRepository extends JpaRepository<InspectorApplication, UUID> {

  Optional<InspectorApplication> findByNormalizedEmail(String normalizedEmail);

  boolean existsByNormalizedEmail(String normalizedEmail);

  List<InspectorApplication> findAllByStatusOrderByCreatedAtAsc(String status);
}
