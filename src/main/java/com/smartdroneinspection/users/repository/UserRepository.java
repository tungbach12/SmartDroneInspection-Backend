package com.smartdroneinspection.users.repository;

import com.smartdroneinspection.users.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByNormalizedEmail(String normalizedEmail);

  Optional<User> findDetailedById(UUID id);

  List<User> findWorkforceByOrganizationId(UUID organizationId);

  /**
   * Every account the platform administrator manages, with its roles.
   *
   * <p>Platform-wide on purpose: an ADMIN manages enterprise workspaces and the
   * administrator-managed accounts inside them, so this is not scoped to one tenant.
   */
  @Query("select distinct u from User u order by u.email")
  List<User> findAllWithRoles();

  @Query(
      "select count(u) > 0 from User u where concat(',', u.roles, ',') like concat('%,', :roleName, ',%')")
  boolean existsByRoleName(@Param("roleName") String roleName);
}
