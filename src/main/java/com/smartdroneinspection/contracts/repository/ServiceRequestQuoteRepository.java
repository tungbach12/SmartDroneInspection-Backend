package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestQuoteRepository extends JpaRepository<ServiceRequestQuote, UUID> {

  Optional<ServiceRequestQuote> findByRequestId(UUID requestId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select quote from ServiceRequestQuote quote where quote.requestId = :requestId")
  Optional<ServiceRequestQuote> findWithLockByRequestId(@Param("requestId") UUID requestId);
}
