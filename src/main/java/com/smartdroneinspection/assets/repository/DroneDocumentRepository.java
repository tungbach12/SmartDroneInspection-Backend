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
}
