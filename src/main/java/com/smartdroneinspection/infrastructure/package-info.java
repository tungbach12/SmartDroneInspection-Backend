@org.springframework.modulith.ApplicationModule(
    displayName = "Infrastructure",
    allowedDependencies = {
      "shared",
      "shared::exception",
      "shared::storage",
      "inspections::spi",
      "contracts::gateway"
    })
package com.smartdroneinspection.infrastructure;
