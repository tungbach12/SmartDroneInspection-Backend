@org.springframework.modulith.ApplicationModule(
    displayName = "Maintenance",
    allowedDependencies = {
      "inspections::spi",
      "shared::api",
      "shared::exception",
      "shared::storage",
      "users",
      "workforce::credential",
      "maintenance::repository"
    })
package com.smartdroneinspection.maintenance;
