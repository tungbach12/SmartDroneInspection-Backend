package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.contracts.domain.GatewayInboundTransaction;
import com.smartdroneinspection.contracts.domain.GatewayInboundTransaction.Status;
import com.smartdroneinspection.contracts.repository.GatewayInboundTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the outcome of inbound gateway notifications in its own transaction, so the record
 * survives even when applying the payment fails and rolls back.
 */
@Service
public class GatewayInboundTransactionService {

  private final GatewayInboundTransactionRepository repository;

  public GatewayInboundTransactionService(GatewayInboundTransactionRepository repository) {
    this.repository = repository;
  }

  /** True when this notification was already handled to a final outcome (anything but ERROR). */
  @Transactional(readOnly = true)
  public boolean isAlreadyHandled(String gateway, String externalId) {
    if (externalId == null || externalId.isBlank()) {
      return false;
    }
    return repository
        .findByGatewayAndExternalId(gateway, externalId)
        .map(existing -> existing.getStatus() != Status.ERROR)
        .orElse(false);
  }

  /** Inserts or updates the record for this notification. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      String gateway,
      String externalId,
      Long orderCode,
      Long amount,
      String accountNumber,
      String payload,
      Status status,
      String detail) {
    GatewayInboundTransaction row =
        (externalId == null || externalId.isBlank())
            ? new GatewayInboundTransaction(gateway, null)
            : repository
                .findByGatewayAndExternalId(gateway, externalId)
                .orElseGet(() -> new GatewayInboundTransaction(gateway, externalId));
    row.record(orderCode, amount, accountNumber, payload, status, detail);
    repository.save(row);
  }
}
