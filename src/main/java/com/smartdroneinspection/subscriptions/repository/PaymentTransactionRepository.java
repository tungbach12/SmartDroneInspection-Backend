package com.smartdroneinspection.subscriptions.repository;

import com.smartdroneinspection.subscriptions.domain.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

  Optional<PaymentTransaction> findByPaypalCaptureId(String paypalCaptureId);

  Optional<PaymentTransaction> findByPaypalOrderId(String paypalOrderId);

  List<PaymentTransaction> findAllBySubscriptionIdOrderByCapturedAtDesc(UUID subscriptionId);
}
