@org.springframework.modulith.ApplicationModule(
    displayName = "Maintenance",
    allowedDependencies = {
      "inspections::spi",
      "shared::api",
      "shared::exception",
      "users",
      "workforce::spi",
      "maintenance::repository"
    })
package com.smartdroneinspection.maintenance;
