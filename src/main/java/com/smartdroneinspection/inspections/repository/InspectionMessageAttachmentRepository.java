package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionMessageAttachment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionMessageAttachmentRepository
    extends JpaRepository<InspectionMessageAttachment, UUID> {

  Optional<InspectionMessageAttachment> findByIdAndMessage_IdAndMessage_InspectionId(
      UUID id, UUID messageId, UUID inspectionId);
}
