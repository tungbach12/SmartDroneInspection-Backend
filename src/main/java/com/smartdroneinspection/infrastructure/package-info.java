@org.springframework.modulith.ApplicationModule(
    displayName = "Infrastructure",
    allowedDependencies = {
      "shared",
      "shared::exception",
      "shared::storage",
      "inspections::spi",
      "subscriptions::gateway",
      "contracts::gateway",
      "maintenance::spi"
    })
package com.smartdroneinspection.infrastructure;
