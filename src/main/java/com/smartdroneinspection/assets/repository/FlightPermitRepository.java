package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Flight permit lookups scoped to the owning organization.
 *
 * <p>MF2-05 needs the permits that apply to one mission rather than the whole file, so the
 * organization-and-asset finder is the one the readiness gate should use; an asset with no
 * applicable permit returns empty rather than a permit belonging to a different asset.
 */
public interface FlightPermitRepository extends JpaRepository<FlightPermit, UUID> {

  Optional<FlightPermit> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<FlightPermit> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
      UUID organizationId, FlightPermitStatus status);

  List<FlightPermit> findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(
      UUID organizationId, UUID assetId);

  /** Permits that currently authorize a flight for this asset, ignoring expiry bookkeeping. */
  List<FlightPermit> findByOrganizationIdAndAssetIdAndStatusIn(
      UUID organizationId, UUID assetId, List<FlightPermitStatus> statuses);
}
