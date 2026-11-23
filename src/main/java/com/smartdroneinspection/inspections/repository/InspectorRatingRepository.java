package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectorRating;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectorRatingRepository extends JpaRepository<InspectorRating, UUID> {

  List<InspectorRating> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

  Optional<InspectorRating> findByInspectionIdAndRaterRole(UUID inspectionId, RaterRole raterRole);

  List<InspectorRating> findByInspectorIdOrderByCreatedAtDesc(UUID inspectorId);

  Page<InspectorRating> findByInspectorIdOrderByCreatedAtDesc(UUID inspectorId, Pageable pageable);

  @Query(
      "select r.inspectorId as inspectorId, r.raterRole as raterRole, count(r) as ratingCount, "
          + "coalesce(sum(r.overallScore), 0) as scoreSum "
          + "from InspectorRating r "
          + "where r.inspectorId in :inspectorIds "
          + "group by r.inspectorId, r.raterRole")
  List<InspectorRatingAggregateProjection> summarizeByInspectorIds(
      @Param("inspectorIds") Collection<UUID> inspectorIds);

  interface InspectorRatingAggregateProjection {
    UUID getInspectorId();

    RaterRole getRaterRole();

    long getRatingCount();

    long getScoreSum();
  }
}
