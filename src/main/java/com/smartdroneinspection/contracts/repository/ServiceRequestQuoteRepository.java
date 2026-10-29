package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestQuoteRepository extends JpaRepository<ServiceRequestQuote, UUID> {

  List<ServiceRequestQuote> findByRequestIdOrderByVersionNoDesc(UUID requestId);

  Optional<ServiceRequestQuote> findFirstByRequestIdOrderByVersionNoDesc(UUID requestId);

  Optional<ServiceRequestQuote> findByRequestIdAndVersionNo(UUID requestId, int versionNo);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select quote from ServiceRequestQuote quote where quote.requestId = :requestId")
  List<ServiceRequestQuote> findWithLockByRequestId(@Param("requestId") UUID requestId);
}
