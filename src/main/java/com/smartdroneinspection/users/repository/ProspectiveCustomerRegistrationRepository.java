package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProspectiveCustomerRegistrationRepository
    extends JpaRepository<ProspectiveCustomerRegistration, UUID> {
  List<ProspectiveCustomerRegistration> findAllByStatusOrderByCreatedAtAsc(String status);

  Optional<ProspectiveCustomerRegistration> findByIdAndStatus(UUID id, String status);

  boolean existsByOrganizationCodeIgnoreCaseAndStatus(String code, String status);

  boolean existsByOrganizationCodeIgnoreCaseAndAssetCodeIgnoreCaseAndStatus(
      String organizationCode, String assetCode, String status);
}
