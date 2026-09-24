package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

  Optional<Evidence> findByInspectionIdAndChecksumSha256(UUID inspectionId, String checksumSha256);

  Optional<Evidence> findByIdAndInspectionIdAndUploadStatus(
      UUID id, UUID inspectionId, UploadStatus uploadStatus);

  List<Evidence> findByInspectionIdAndUploadStatusOrderByCreatedAtDesc(
      UUID inspectionId, UploadStatus uploadStatus);
}
