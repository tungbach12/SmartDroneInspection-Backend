package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.RatingCriterionConfig;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingCriterionConfigRepository
    extends JpaRepository<RatingCriterionConfig, UUID> {

  List<RatingCriterionConfig> findAllByOrderByRaterRoleAscDisplayOrderAscCreatedAtAsc();

  List<RatingCriterionConfig> findAllByIsActiveTrueOrderByRaterRoleAscDisplayOrderAscCreatedAtAsc();

  List<RatingCriterionConfig> findAllByRaterRoleAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(
      RaterRole raterRole);

  List<RatingCriterionConfig> findAllByRaterRoleOrderByDisplayOrderAscCreatedAtAsc(
      RaterRole raterRole);

  boolean existsByRaterRoleAndCodeIgnoreCase(RaterRole raterRole, String code);

  Optional<RatingCriterionConfig> findByRaterRoleAndCode(RaterRole raterRole, String code);

  long countByRaterRoleAndIsActiveTrue(RaterRole raterRole);
}
