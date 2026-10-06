package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.ProviderOrganization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderOrganizationRepository extends JpaRepository<ProviderOrganization, UUID> {

  Optional<ProviderOrganization> findByTaxCode(String taxCode);

  boolean existsByTaxCode(String taxCode);
}
