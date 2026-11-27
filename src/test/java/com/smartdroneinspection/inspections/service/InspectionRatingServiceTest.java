package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.request.UpsertInspectorRatingRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionRatingBundleResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectorRatingProfileResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectorRating;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.inspections.events.InspectorRatedEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.InspectorRatingRepository;
import com.smartdroneinspection.inspections.repository.InspectorReliabilityEventRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InspectionRatingServiceTest {

  @Mock private InspectorRatingRepository ratings;
  @Mock private InspectorReliabilityEventRepository reliabilityEvents;
  @Mock private InspectionRepository inspections;
  @Mock private AssetRepository assets;
  @Mock private UserAccess userAccess;
  @Mock private ApplicationEventPublisher events;

  private InspectionRatingService service;
  private final Instant fixedNow = Instant.parse("2026-11-23T12:00:00Z");

  private final UUID inspectionId = UUID.randomUUID();
  private final UUID ownerCustomerId = UUID.randomUUID();
  private final UUID foreignCustomerId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new InspectionRatingService(
            ratings,
            inspections,
            assets,
            userAccess,
            events,
            Clock.fixed(fixedNow, ZoneOffset.UTC),
            null,
            reliabilityEvents);
  }

  @Test
  void customerCanRateCompletedOwnedInspectionAndPublishesEvent() {
    Inspection inspection = completedInspection();
    when(userAccess.findActiveUser(ownerCustomerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(ownerCustomerId, Set.of(Roles.CUSTOMER), "Owner Cust")));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(ratings.findByInspectionIdAndRaterRole(inspectionId, RaterRole.CUSTOMER))
        .thenReturn(Optional.empty());
    when(ratings.saveAndFlush(any(InspectorRating.class)))
        .thenAnswer(
            inv -> {
              InspectorRating r = inv.getArgument(0);
              ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
              return r;
            });
    when(ratings.findByInspectionIdOrderByCreatedAtAsc(inspectionId)).thenReturn(List.of());

    InspectionRatingBundleResponse result =
        service.upsertForInspection(
            ownerCustomerId,
            inspectionId,
            new UpsertInspectorRatingRequest(
                5,
                Map.of("evidenceQuality", 5, "communication", 5, "professionalism", 4),
                "Great flight execution"));

    assertThat(result.canRate()).isTrue();
    assertThat(result.actorRole()).isEqualTo("CUSTOMER");
    assertThat(result.expectedCriteriaKeys())
        .containsExactly("evidenceQuality", "communication", "professionalism");

    ArgumentCaptor<InspectorRatedEvent> eventCaptor =
        ArgumentCaptor.forClass(InspectorRatedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().inspectorId()).isEqualTo(inspectorId);
    assertThat(eventCaptor.getValue().raterRole()).isEqualTo("CUSTOMER");
    assertThat(eventCaptor.getValue().overallScore()).isEqualTo(5);
    assertThat(eventCaptor.getValue().updated()).isFalse();
  }

  @Test
  void rejectsRatingBeforeInspectionIsCompleted() {
    Inspection inspection =
        new Inspection(ownerCustomerId, UUID.randomUUID(), inspectorId, null, null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.UNDER_REVIEW);

    when(userAccess.findActiveUser(managerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), "Manager 1")));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    assertThatThrownBy(
            () ->
                service.upsertForInspection(
                    managerId,
                    inspectionId,
                    new UpsertInspectorRatingRequest(
                        4,
                        Map.of("sopAndSafety", 4, "technicalAccuracy", 4, "timeliness", 4),
                        null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code()).isEqualTo("INSPECTION_NOT_COMPLETED"));
  }

  @Test
  void rejectsAdminWritesAndCrossTenantCustomerWrites() {
    Inspection inspection = completedInspection();
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    when(userAccess.findActiveUser(adminId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(adminId, Set.of(Roles.ADMIN), "Admin 1")));
    assertThatThrownBy(
            () ->
                service.upsertForInspection(
                    adminId,
                    inspectionId,
                    new UpsertInspectorRatingRequest(
                        5,
                        Map.of("sopAndSafety", 5, "technicalAccuracy", 5, "timeliness", 5),
                        null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("FORBIDDEN"));

    when(userAccess.findActiveUser(foreignCustomerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    foreignCustomerId, Set.of(Roles.CUSTOMER), "Foreign Customer")));
    assertThatThrownBy(
            () ->
                service.upsertForInspection(
                    foreignCustomerId,
                    inspectionId,
                    new UpsertInspectorRatingRequest(
                        5,
                        Map.of("evidenceQuality", 5, "communication", 5, "professionalism", 5),
                        null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void customerProfileViewHidesIndividualCommentsWhileManagerSeesHistory() {
    InspectorRating custRating =
        new InspectorRating(
            inspectionId,
            inspectorId,
            ownerCustomerId,
            RaterRole.CUSTOMER,
            4,
            Map.of("evidenceQuality", 4, "communication", 5, "professionalism", 4),
            "Solid flight",
            fixedNow);
    InspectorRating mgrRating =
        new InspectorRating(
            inspectionId,
            inspectorId,
            managerId,
            RaterRole.MANAGER,
            5,
            Map.of("sopAndSafety", 5, "technicalAccuracy", 5, "timeliness", 5),
            "Clean SOP",
            fixedNow);
    when(ratings.findByInspectorIdOrderByCreatedAtDesc(inspectorId))
        .thenReturn(List.of(mgrRating, custRating));

    when(userAccess.findActiveUser(ownerCustomerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(ownerCustomerId, Set.of(Roles.CUSTOMER), "Owner Cust")));
    InspectorRatingProfileResponse customerView =
        service.getInspectorSummaryAndHistory(ownerCustomerId, inspectorId, PageRequest.of(0, 10));
    assertThat(customerView.historyVisible()).isFalse();
    assertThat(customerView.ratings()).isEmpty();
    assertThat(customerView.summary().averageRating()).isEqualTo(4.5);
    assertThat(customerView.summary().ratingCount()).isEqualTo(2);
    assertThat(customerView.noShowCount()).isNull();

    when(userAccess.findActiveUser(managerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), "Manager 1")));
    when(ratings.findByInspectorIdOrderByCreatedAtDesc(inspectorId, PageRequest.of(0, 10)))
        .thenReturn(new PageImpl<>(List.of(mgrRating, custRating)));
    when(inspections.findAllById(any())).thenReturn(List.of());
    when(reliabilityEvents.countByInspectorIdAndEventType(inspectorId, EventType.NO_SHOW))
        .thenReturn(1L);
    InspectorRatingProfileResponse managerView =
        service.getInspectorSummaryAndHistory(managerId, inspectorId, PageRequest.of(0, 10));
    assertThat(managerView.historyVisible()).isTrue();
    assertThat(managerView.ratings()).hasSize(2);
    assertThat(managerView.summary().customerAverageRating()).isEqualTo(4.0);
    assertThat(managerView.summary().managerAverageRating()).isEqualTo(5.0);
    assertThat(managerView.noShowCount()).isEqualTo(1L);
  }

  private Inspection completedInspection() {
    Inspection inspection =
        new Inspection(ownerCustomerId, UUID.randomUUID(), inspectorId, null, null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.COMPLETED);
    return inspection;
  }
}
