package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.request.CreateRatingCriterionRequest;
import com.smartdroneinspection.inspections.domain.RatingCriterionConfig;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.inspections.repository.InspectorRatingRepository;
import com.smartdroneinspection.inspections.repository.RatingCriterionConfigRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RatingCriteriaConfigServiceTest {

  private final RatingCriterionConfigRepository repository =
      mock(RatingCriterionConfigRepository.class);
  private final InspectorRatingRepository ratings = mock(InspectorRatingRepository.class);
  private final RatingCriteriaConfigService service =
      new RatingCriteriaConfigService(repository, ratings);

  @Test
  void createsCriterionWithNextOrderAndRejectsDuplicates() {
    RatingCriterionConfig existing =
        new RatingCriterionConfig(
            RaterRole.CUSTOMER, "communication", "Communication", "Giao tiếp", null, null, 3, true);
    when(repository.existsByRaterRoleAndCodeIgnoreCase(RaterRole.CUSTOMER, "reportClarity"))
        .thenReturn(false);
    when(repository.findAllByRaterRoleOrderByDisplayOrderAscCreatedAtAsc(RaterRole.CUSTOMER))
        .thenReturn(List.of(existing));
    when(repository.saveAndFlush(any(RatingCriterionConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var created =
        service.create(
            new CreateRatingCriterionRequest(
                RaterRole.CUSTOMER,
                "reportClarity",
                "Report Clarity",
                "Độ rõ ràng báo cáo",
                "Clear takeaways",
                "Rõ ràng dễ đọc",
                null,
                true));

    assertThat(created.code()).isEqualTo("reportClarity");
    assertThat(created.displayOrder()).isEqualTo(4);
    assertThat(created.active()).isTrue();

    when(repository.existsByRaterRoleAndCodeIgnoreCase(RaterRole.CUSTOMER, "reportClarity"))
        .thenReturn(true);
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateRatingCriterionRequest(
                        RaterRole.CUSTOMER,
                        "reportClarity",
                        "Duplicate",
                        "Trùng",
                        null,
                        null,
                        null,
                        true)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code()).isEqualTo("DUPLICATE_CRITERION_CODE"));
  }

  @Test
  void preventsDeactivatingLastActiveCriterionAndDeletingUsedCriterion() {
    UUID id = UUID.randomUUID();
    RatingCriterionConfig onlyActive =
        new RatingCriterionConfig(
            RaterRole.MANAGER, "sopAndSafety", "SOP & safety", "Tuân thủ SOP", null, null, 1, true);
    when(repository.findById(id)).thenReturn(Optional.of(onlyActive));
    when(repository.countByRaterRoleAndIsActiveTrue(RaterRole.MANAGER)).thenReturn(1L);

    assertThatThrownBy(() -> service.toggleActive(id))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("LAST_ACTIVE_CRITERION"));

    when(repository.countByRaterRoleAndIsActiveTrue(RaterRole.MANAGER)).thenReturn(2L);
    when(ratings.existsByRaterRoleAndCriterionCode("MANAGER", "sopAndSafety")).thenReturn(true);

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("CRITERION_IN_USE"));

    when(ratings.existsByRaterRoleAndCriterionCode("MANAGER", "sopAndSafety")).thenReturn(false);
    service.delete(id);
    verify(repository).delete(onlyActive);
  }
}
