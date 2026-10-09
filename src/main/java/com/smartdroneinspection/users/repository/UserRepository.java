package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  @EntityGraph(attributePaths = "roleAssignments")
  Optional<User> findByNormalizedEmail(String normalizedEmail);

  @EntityGraph(attributePaths = "roleAssignments")
  Optional<User> findDetailedById(UUID id);

  @EntityGraph(attributePaths = "roleAssignments")
  List<User> findWorkforceByOrganizationId(UUID organizationId);

  boolean existsByRoleAssignments_Role(UserRole role);
}
