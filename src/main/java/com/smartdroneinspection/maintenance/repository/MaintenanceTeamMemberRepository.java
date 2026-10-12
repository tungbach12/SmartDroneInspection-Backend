package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.MaintenanceTeamMember;
import com.smartdroneinspection.maintenance.domain.enums.TeamMemberRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTeamMemberRepository
    extends JpaRepository<MaintenanceTeamMember, UUID> {

  List<MaintenanceTeamMember> findByWorkOrderIdAndActiveTrueOrderByCreatedAtAsc(UUID workOrderId);

  Optional<MaintenanceTeamMember> findByWorkOrderIdAndMemberRoleAndActiveTrue(
      UUID workOrderId, TeamMemberRole memberRole);

  List<MaintenanceTeamMember> findByWorkOrderIdAndEngineerUserIdAndMemberRoleAndActiveTrue(
      UUID workOrderId, UUID engineerUserId, TeamMemberRole memberRole);

  boolean existsByWorkOrderIdAndEngineerUserIdAndActiveTrue(UUID workOrderId, UUID engineerUserId);
}
