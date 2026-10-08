@org.springframework.modulith.ApplicationModule(
    displayName = "Maintenance",
    allowedDependencies = {
      "assets::domain",
      "assets::repository",
      "inspections",
      "inspections::spi",
      "inspectionrequests",
      "shared",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "shared::storage",
      "users"
    })
package com.smartdroneinspection.maintenance;
