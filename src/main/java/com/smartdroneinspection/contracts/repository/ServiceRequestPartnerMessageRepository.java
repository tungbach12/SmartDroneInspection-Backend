package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestPartnerMessageRepository
    extends JpaRepository<ServiceRequestPartnerMessage, UUID> {

  List<ServiceRequestPartnerMessage> findByRequestIdAndInspectorIdOrderByCreatedAtAscIdAsc(
      UUID requestId, UUID inspectorId);

  List<ServiceRequestPartnerMessage> findByRequestIdAndInspectorIdAndAuthorUserIdNotAndReadAtIsNull(
      UUID requestId, UUID inspectorId, UUID authorUserId);
}
