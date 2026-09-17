package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  @EntityGraph(attributePaths = "roleAssignments")
  Optional<User> findByNormalizedEmail(String normalizedEmail);

  @EntityGraph(attributePaths = "roleAssignments")
  Optional<User> findDetailedById(UUID id);

  boolean existsByRoleAssignments_Role(UserRole role);
}
