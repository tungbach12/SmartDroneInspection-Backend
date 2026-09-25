package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.PeerReview;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeerReviewRepository extends JpaRepository<PeerReview, UUID> {

  Optional<PeerReview> findByReportVersionId(UUID reportVersionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select review from PeerReview review where review.reportVersionId = :versionId")
  Optional<PeerReview> findForUpdateByReportVersionId(@Param("versionId") UUID versionId);

  List<PeerReview> findByReviewerUserIdOrderByAssignedAtDesc(UUID reviewerUserId);
}
