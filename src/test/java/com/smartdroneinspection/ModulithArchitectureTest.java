package com.smartdroneinspection;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

  ApplicationModules modules = ApplicationModules.of(SmartDroneInspectionApplication.class);

  @Test
  void verifiesModularStructure() {
    modules.verify();
  }
}
