package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

  @Mock private EvidenceRepository evidence;
  @Mock private FieldSessionRepository fieldSessions;
  @Mock private FindingRepository findings;
  @Mock private InspectionReportRepository reports;
  @Mock private InspectionRepository inspections;
  @Mock private EvidenceObjectStore objectStore;
  @Mock private UserAccess userAccess;

  private EvidenceService evidenceService;

  private final UUID inspectionId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID otherUserId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    evidenceService =
        new EvidenceService(
            inspections,
            evidence,
            reports,
            findings,
            fieldSessions,
            Optional.of(objectStore),
            userAccess);
  }

  @Test
  @DisplayName("MANAGER has platform scope and can access inspection across organizations")
  void managerHasPlatformScope() {
    UserAccess.ActiveUser manager =
        new UserAccess.ActiveUser(
            managerId,
            Set.of(Roles.MANAGER),
            null,
            "PLATFORM",
            "Platform Manager",
            "manager@platform.test",
            null,
            null,
            null,
            null);

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    when(userAccess.findActiveUser(managerId)).thenReturn(Optional.of(manager));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    Inspection result = evidenceService.requireInspectionInScope(managerId, inspectionId);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(inspectionId);
  }

  @Test
  @DisplayName("ADMIN has platform scope and can access inspection")
  void adminHasPlatformScope() {
    UserAccess.ActiveUser admin =
        new UserAccess.ActiveUser(
            adminId,
            Set.of(Roles.ADMIN),
            null,
            "PLATFORM",
            "Platform Admin",
            "admin@platform.test",
            null,
            null,
            null,
            null);

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    when(userAccess.findActiveUser(adminId)).thenReturn(Optional.of(admin));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    Inspection result = evidenceService.requireInspectionInScope(adminId, inspectionId);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(inspectionId);
  }

  @Test
  @DisplayName("Unassigned user without management role is refused access")
  void unassignedUserIsRefused() {
    UserAccess.ActiveUser stranger =
        new UserAccess.ActiveUser(
            otherUserId,
            Set.of(Roles.INSPECTOR),
            orgId,
            "CUSTOMER_ORGANIZATION",
            "Other Guy",
            "other@asset.test",
            null,
            null,
            null,
            null);

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), inspectorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    when(userAccess.findActiveUser(otherUserId)).thenReturn(Optional.of(stranger));
    when(inspections.findByIdAndOrganizationId(inspectionId, orgId))
        .thenReturn(Optional.of(inspection));

    assertThatThrownBy(() -> evidenceService.requireInspectionInScope(otherUserId, inspectionId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Inspection not found");
  }
}
