package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.DroneDocument;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DroneDocumentRepository extends JpaRepository<DroneDocument, UUID> {

  /** MF1 inventory listing; service verifies ownership before calling this query. */
  List<DroneDocument> findAllByDrone_IdOrderByCreatedAtDesc(UUID droneId);

  /** Tenant-scoped readiness selection through the Drone's owning organization. */
  @Query(
      "select document from DroneDocument document "
          + "join document.drone drone "
          + "where drone.organizationId = :organizationId "
          + "and drone.id = :droneId "
          + "and document.id in :documentIds")
  List<DroneDocument> findForReadiness(
      @Param("organizationId") UUID organizationId,
      @Param("droneId") UUID droneId,
      @Param("documentIds") Collection<UUID> documentIds);

  /** All tenant-owned Drone documents, reached through the Drone owner. */
  @Query(
      "select document from DroneDocument document "
          + "join document.drone drone "
          + "where drone.organizationId = :organizationId "
          + "and drone.id = :droneId")
  List<DroneDocument> findAllForReadiness(
      @Param("organizationId") UUID organizationId, @Param("droneId") UUID droneId);
}
