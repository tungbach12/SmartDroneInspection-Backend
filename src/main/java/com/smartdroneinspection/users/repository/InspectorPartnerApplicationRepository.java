package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.InspectorPartnerApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectorPartnerApplicationRepository
    extends JpaRepository<InspectorPartnerApplication, UUID> {
  List<InspectorPartnerApplication> findAllByStatusOrderByCreatedAtAsc(String status);

  Optional<InspectorPartnerApplication> findByIdAndStatus(UUID id, String status);

  boolean existsByNormalizedEmailAndStatus(String normalizedEmail, String status);
}
