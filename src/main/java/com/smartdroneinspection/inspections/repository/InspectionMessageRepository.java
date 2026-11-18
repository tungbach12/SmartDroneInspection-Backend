package com.smartdroneinspection.inspections.repository;

import com.smartdroneinspection.inspections.domain.InspectionMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionMessageRepository extends JpaRepository<InspectionMessage, UUID> {

  List<InspectionMessage> findByInspectionIdOrderByCreatedAtAscIdAsc(UUID inspectionId);

  List<InspectionMessage> findByInspectionIdAndAuthorUserIdNotAndReadAtIsNull(
      UUID inspectionId, UUID authorUserId);
}
