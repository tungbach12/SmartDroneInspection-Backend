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
  void discoversTargetModuleRoots() {
    assertThat(modules.getModuleByName("inspections")).isPresent();
    assertThat(modules.getModuleByName("maintenance")).isPresent();
    assertThat(modules.getModuleByName("notifications")).isPresent();
    assertThat(modules.getModuleByName("dashboard")).isPresent();
    assertThat(modules.getModuleByName("infrastructure")).isPresent();
    assertThat(modules.getModuleByName("assets")).isPresent();
    assertThat(modules.getModuleByName("users")).isPresent();
    assertThat(modules.getModuleByName("subscriptions")).isPresent();
    assertThat(modules.getModuleByName("workforce")).isPresent();
  }

  @Test
  void doesNotDiscoverInspectionRequests() {
    assertThat(modules.getModuleByName("inspectionrequests")).isEmpty();
  }
}
