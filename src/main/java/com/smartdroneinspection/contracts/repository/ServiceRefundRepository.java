package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRefund;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRefundRepository extends JpaRepository<ServiceRefund, UUID> {
  boolean existsByReferenceIgnoreCase(String reference);
}
