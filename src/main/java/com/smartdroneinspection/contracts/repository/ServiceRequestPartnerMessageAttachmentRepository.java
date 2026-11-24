package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessageAttachment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestPartnerMessageAttachmentRepository
    extends JpaRepository<ServiceRequestPartnerMessageAttachment, UUID> {

  Optional<ServiceRequestPartnerMessageAttachment>
      findByIdAndMessage_IdAndMessage_RequestIdAndMessage_InspectorId(
          UUID id, UUID messageId, UUID requestId, UUID inspectorId);
}
