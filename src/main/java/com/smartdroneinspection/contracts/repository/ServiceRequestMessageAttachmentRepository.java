package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestMessageAttachment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestMessageAttachmentRepository
    extends JpaRepository<ServiceRequestMessageAttachment, UUID> {

  Optional<ServiceRequestMessageAttachment> findByIdAndMessage_IdAndMessage_RequestId(
      UUID id, UUID messageId, UUID requestId);

  java.util.List<ServiceRequestMessageAttachment> findByMessageId(UUID messageId);
}
