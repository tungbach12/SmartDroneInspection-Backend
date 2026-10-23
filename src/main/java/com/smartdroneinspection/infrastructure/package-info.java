@org.springframework.modulith.ApplicationModule(
    displayName = "Infrastructure",
    allowedDependencies = {
      "shared",
      "shared::storage",
      "inspections::spi",
      "subscriptions::gateway",
      "maintenance::spi"
    })
package com.smartdroneinspection.infrastructure;
