@org.springframework.modulith.ApplicationModule(
    displayName = "Infrastructure",
    allowedDependencies = {
      "shared",
      "shared::exception",
      "shared::storage",
      "inspections::spi",
      "contracts::gateway",
      "maintenance::spi"
    })
package com.smartdroneinspection.infrastructure;
