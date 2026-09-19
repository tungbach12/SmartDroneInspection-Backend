package com.smartdroneinspection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

  ApplicationModules modules = ApplicationModules.of(SmartDroneInspectionApplication.class);

  @Test
  void verifiesModularStructure() {
    modules.verify();
  }

  @Test
  void discoversInspectionRequestsCapability() {
    assertThat(modules.getModuleByName("inspectionrequests")).isPresent();
  }
}
