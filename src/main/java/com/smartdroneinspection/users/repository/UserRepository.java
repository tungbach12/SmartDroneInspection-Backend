package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

  @EntityGraph(attributePaths = "roleAssignments")
  Optional<User> findByNormalizedEmail(String normalizedEmail);

  @EntityGraph(attributePaths = "roleAssignments")
  Optional<User> findDetailedById(UUID id);

  @EntityGraph(attributePaths = "roleAssignments")
  List<User> findWorkforceByOrganizationId(UUID organizationId);

  /**
   * Every account the platform administrator manages, with its roles.
   *
   * <p>Platform-wide on purpose: an ADMIN manages enterprise workspaces and the
   * administrator-managed accounts inside them, so this is not scoped to one tenant.
   */
  @EntityGraph(attributePaths = "roleAssignments")
  @Query("select distinct u from User u order by u.email")
  List<User> findAllWithRoles();

  boolean existsByRoleAssignments_Role(UserRole role);
}
