@org.springframework.modulith.ApplicationModule(
    displayName = "Infrastructure",
    allowedDependencies = {
      "shared",
      "shared::storage",
      "inspections::spi",
      "subscriptions::gateway"
    })
package com.smartdroneinspection.infrastructure;
