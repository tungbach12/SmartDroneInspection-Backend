package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

  Optional<Organization> findByCode(String code);
}
