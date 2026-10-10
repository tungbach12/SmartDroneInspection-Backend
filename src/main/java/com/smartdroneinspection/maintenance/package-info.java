@org.springframework.modulith.ApplicationModule(
    displayName = "Maintenance",
    allowedDependencies = {
      "inspections",
      "shared",
      "users",
      "workforce",
      "workforce::repository",
      "maintenance::repository"
    })
package com.smartdroneinspection.maintenance;
