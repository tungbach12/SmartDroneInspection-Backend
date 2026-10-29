package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestMessageRepository
    extends JpaRepository<ServiceRequestMessage, UUID> {

  List<ServiceRequestMessage> findByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
}
