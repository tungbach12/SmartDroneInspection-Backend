package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Inspector-drone pairing lookups, scoped to the owning organization.
 *
 * <p>MF2-01/02 answer a pairing, and two inspectors answering the same pairing at once would
 * produce two responses, so the response-bearing finders take a pessimistic lock.
 */
public interface AssetPairAssignmentRepository extends JpaRepository<AssetPairAssignment, UUID> {

  Optional<AssetPairAssignment> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Query(
      "select pairing from AssetPairAssignment pairing "
          + "where pairing.organizationId = :organizationId "
          + "and pairing.id = :pairId and pairing.assetId = :assetId")
  Optional<AssetPairAssignment> findForReadiness(
      @Param("organizationId") UUID organizationId,
      @Param("pairId") UUID pairId,
      @Param("assetId") UUID assetId);

  /** The single live pairing for an asset, mirroring uq_asset_pair_assignments_active_asset. */
  Optional<AssetPairAssignment> findByOrganizationIdAndAssetIdAndStatus(
      UUID organizationId, UUID assetId, AssetPairAssignmentStatus status);

  List<AssetPairAssignment> findByOrganizationIdOrderByAssignedAtDesc(UUID organizationId);

  /** Pairings an inspector has been given but has not yet answered (MF2-01 inbox). */
  List<AssetPairAssignment> findByOrganizationIdAndInspectorUserIdAndAssignmentResponseIsNull(
      UUID organizationId, UUID inspectorUserId);

  List<AssetPairAssignment> findByInspectorUserIdAndAssignmentResponse(
      UUID inspectorUserId, AssignmentResponse response);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select pairing from AssetPairAssignment pairing "
          + "where pairing.id = :id and pairing.organizationId = :organizationId")
  Optional<AssetPairAssignment> findWithLockByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  /**
   * The live pairing for an asset, locked. MF2-10 starts a session from this pairing, so the read
   * must not race a second activation or a supersede.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select pairing from AssetPairAssignment pairing "
          + "where pairing.organizationId = :organizationId and pairing.assetId = :assetId "
          + "and pairing.status = com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus.ACTIVE")
  Optional<AssetPairAssignment> findActiveWithLockByOrganizationIdAndAssetId(
      @Param("organizationId") UUID organizationId, @Param("assetId") UUID assetId);
}
