package com.smartdroneinspection.assets.repository;

import com.smartdroneinspection.assets.domain.DroneDocument;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DroneDocumentRepository extends JpaRepository<DroneDocument, UUID> {

  @Query(
      "select document from DroneDocument document "
          + "join Drone drone on drone.id = document.droneId "
          + "where drone.organizationId = :organizationId "
          + "and document.droneId = :droneId "
          + "and document.id in :documentIds")
  List<DroneDocument> findForReadiness(
      @Param("organizationId") UUID organizationId,
      @Param("droneId") UUID droneId,
      @Param("documentIds") Collection<UUID> documentIds);

  /**
   * Every document the Drone holds, reached through the Drone's own organization.
   *
   * <p>The join is what makes this tenant-safe: {@code drone_documents} has no organization column
   * of its own, so filtering on the Drone's owner is the only way a caller cannot reach another
   * organization's documents by guessing a Drone id.
   */
  @Query(
      "select document from DroneDocument document "
          + "join Drone drone on drone.id = document.droneId "
          + "where drone.organizationId = :organizationId "
          + "and document.droneId = :droneId")
  List<DroneDocument> findAllForReadiness(
      @Param("organizationId") UUID organizationId, @Param("droneId") UUID droneId);
}
