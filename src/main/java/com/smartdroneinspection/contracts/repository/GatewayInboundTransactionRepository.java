package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.GatewayInboundTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayInboundTransactionRepository
    extends JpaRepository<GatewayInboundTransaction, UUID> {

  Optional<GatewayInboundTransaction> findByGatewayAndExternalId(String gateway, String externalId);
}
