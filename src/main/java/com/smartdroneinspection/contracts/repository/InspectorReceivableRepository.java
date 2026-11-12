package com.smartdroneinspection.contracts.repository;

import com.smartdroneinspection.contracts.domain.InspectorReceivable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectorReceivableRepository extends JpaRepository<InspectorReceivable, UUID> {
  Optional<InspectorReceivable> findByRefundId(UUID refundId);

  List<InspectorReceivable> findAllByOccurrenceId(UUID occurrenceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from InspectorReceivable r where r.id = :id")
  Optional<InspectorReceivable> findByIdForUpdate(@Param("id") UUID id);
}
