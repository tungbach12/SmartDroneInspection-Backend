package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.PeerReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeerReviewRepository extends JpaRepository<PeerReview, UUID> {}
