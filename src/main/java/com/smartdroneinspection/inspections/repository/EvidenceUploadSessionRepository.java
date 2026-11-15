package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.EvidenceUploadSession;
import com.smartdroneinspection.inspections.domain.enums.EvidenceUploadSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceUploadSessionRepository
    extends JpaRepository<EvidenceUploadSession, UUID> {

  Optional<EvidenceUploadSession> findByIdAndInspectionId(UUID id, UUID inspectionId);

  Optional<EvidenceUploadSession> findByEvidenceId(UUID evidenceId);

  List<EvidenceUploadSession> findByStatusAndExpiresAtBefore(
      EvidenceUploadSessionStatus status, Instant cutoff);
}
