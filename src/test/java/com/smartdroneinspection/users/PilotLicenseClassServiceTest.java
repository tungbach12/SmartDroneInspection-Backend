package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.api.dto.request.SavePilotLicenseClassRequest;
import com.smartdroneinspection.users.domain.PilotLicenseClass;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.PilotLicenseClassRepository;
import com.smartdroneinspection.users.service.PilotLicenseClassService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PilotLicenseClassServiceTest {

  @Mock private PilotLicenseClassRepository classes;
  @Mock private InspectorProfileRepository inspectorProfiles;

  private PilotLicenseClassService service;

  @BeforeEach
  void setUp() {
    service = new PilotLicenseClassService(classes, inspectorProfiles);
  }

  @Test
  void create_rejectsDuplicateNamesIgnoringCase() {
    when(classes.existsByNameIgnoreCase("Class A")).thenReturn(true);

    assertThatThrownBy(
            () -> service.create(new SavePilotLicenseClassRequest(" Class A ", null, true)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Hạng bằng lái đã tồn tại");
  }

  @Test
  void requireActiveName_returnsCanonicalValue() {
    PilotLicenseClass licenseClass = new PilotLicenseClass("Class A", null);
    when(classes.findByNameIgnoreCaseAndActiveTrue("class a"))
        .thenReturn(Optional.of(licenseClass));

    assertThat(service.requireActiveName(" class a ")).isEqualTo("Class A");
  }

  @Test
  void requireActiveName_rejectsInactiveOrUnknownValue() {
    when(classes.findByNameIgnoreCaseAndActiveTrue("Class A")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireActiveName("Class A"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception ->
                assertThat(((BusinessException) exception).status())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void deactivate_marksOptionInactiveInsteadOfDeletingIt() {
    UUID id = UUID.randomUUID();
    PilotLicenseClass licenseClass = new PilotLicenseClass("Class A", null);
    when(classes.findById(id)).thenReturn(Optional.of(licenseClass));
    when(classes.saveAndFlush(licenseClass)).thenReturn(licenseClass);

    service.deactivate(id);

    assertThat(licenseClass.isActive()).isFalse();
    verify(classes).saveAndFlush(licenseClass);
  }
}
