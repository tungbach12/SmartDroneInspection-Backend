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

  @Test
  void discoversApprovedScaffoldedCapabilityRoots() {
    assertThat(modules.getModuleByName("inspections")).isPresent();
    assertThat(modules.getModuleByName("maintenance")).isPresent();
    assertThat(modules.getModuleByName("notifications")).isPresent();
    assertThat(modules.getModuleByName("dashboard")).isPresent();
    assertThat(modules.getModuleByName("infrastructure")).isPresent();
  }
}
