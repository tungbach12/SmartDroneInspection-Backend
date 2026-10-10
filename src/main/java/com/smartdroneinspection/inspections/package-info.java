@org.springframework.modulith.ApplicationModule(
    displayName = "Inspections",
    allowedDependencies = {
      "assets::domain",
      "assets::enums",
      "assets::repository",
      "assets::readiness",
      "workforce::credential",
      "users",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "shared::storage"
    })
package com.smartdroneinspection.inspections;
