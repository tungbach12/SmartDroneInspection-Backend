package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.Asset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
  @EntityGraph(attributePaths = "documents")
  Optional<Asset> findDetailedByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Asset> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

  Page<Asset> findByOrganizationId(UUID organizationId, Pageable pageable);

  @Query(
      "select a from Asset a where a.organizationId = :organizationId "
          + "and (lower(a.code) like lower(concat('%', :search, '%')) "
          + "or lower(a.name) like lower(concat('%', :search, '%'))) ")
  Page<Asset> searchByOrganizationId(
      @Param("organizationId") UUID organizationId,
      @Param("search") String search,
      Pageable pageable);
}
