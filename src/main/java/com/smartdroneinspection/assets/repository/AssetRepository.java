package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
  @EntityGraph(attributePaths = "documents")
  Optional<Asset> findDetailedById(UUID id);

  @EntityGraph(attributePaths = "documents")
  List<Asset> findDetailedByIdIn(Collection<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select asset from Asset asset where asset.id = :id")
  Optional<Asset> findWithLockById(@Param("id") UUID id);

  Page<Asset> findByStatus(AssetStatus status, Pageable pageable);

  @Query(
      "select a from Asset a where "
          + "lower(a.code) like lower(concat('%', :search, '%')) "
          + "or lower(a.name) like lower(concat('%', :search, '%'))")
  Page<Asset> searchAll(@Param("search") String search, Pageable pageable);

  @Query(
      "select a from Asset a where a.status = :status "
          + "and (lower(a.code) like lower(concat('%', :search, '%')) "
          + "or lower(a.name) like lower(concat('%', :search, '%')))")
  Page<Asset> searchAllByStatus(
      @Param("status") AssetStatus status, @Param("search") String search, Pageable pageable);

  long countByStatus(AssetStatus status);

  @Query(
      value = "SELECT count(*) FROM assets WHERE jsonb_array_length(inspection_items) > 0",
      nativeQuery = true)
  long countAllWithCheckpoints();

  // --- Owner-based access: owner_customer_id is the sole tenant key. ---

  @EntityGraph(attributePaths = "documents")
  Optional<Asset> findDetailedByIdAndOwnerCustomerId(UUID id, UUID ownerCustomerId);

  Optional<Asset> findByIdAndOwnerCustomerId(UUID id, UUID ownerCustomerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select asset from Asset asset where asset.id = :id "
          + "and asset.ownerCustomerId = :ownerCustomerId")
  Optional<Asset> findWithLockByIdAndOwnerCustomerId(
      @Param("id") UUID id, @Param("ownerCustomerId") UUID ownerCustomerId);

  boolean existsByOwnerCustomerIdAndCode(UUID ownerCustomerId, String code);

  Optional<Asset> findByOwnerCustomerIdAndCode(UUID ownerCustomerId, String code);

  Page<Asset> findByOwnerCustomerId(UUID ownerCustomerId, Pageable pageable);

  Page<Asset> findByOwnerCustomerIdAndStatus(
      UUID ownerCustomerId, AssetStatus status, Pageable pageable);

  long countByOwnerCustomerId(UUID ownerCustomerId);

  long countByOwnerCustomerIdAndStatus(UUID ownerCustomerId, AssetStatus status);

  @Query(
      value =
          "SELECT count(*) FROM assets WHERE owner_customer_id = :ownerCustomerId "
              + "AND jsonb_array_length(inspection_items) > 0",
      nativeQuery = true)
  long countByOwnerCustomerIdWithCheckpoints(@Param("ownerCustomerId") UUID ownerCustomerId);

  @Query(
      "select a from Asset a where a.ownerCustomerId = :ownerCustomerId "
          + "and (lower(a.code) like lower(concat('%', :search, '%')) "
          + "or lower(a.name) like lower(concat('%', :search, '%')))")
  Page<Asset> searchByOwnerCustomerId(
      @Param("ownerCustomerId") UUID ownerCustomerId,
      @Param("search") String search,
      Pageable pageable);

  @Query(
      "select a from Asset a where a.ownerCustomerId = :ownerCustomerId and a.status = :status "
          + "and (lower(a.code) like lower(concat('%', :search, '%')) "
          + "or lower(a.name) like lower(concat('%', :search, '%')))")
  Page<Asset> searchByOwnerCustomerIdAndStatus(
      @Param("ownerCustomerId") UUID ownerCustomerId,
      @Param("status") AssetStatus status,
      @Param("search") String search,
      Pageable pageable);
}
