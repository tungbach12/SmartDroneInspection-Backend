@org.springframework.modulith.ApplicationModule(
    displayName = "Dashboard",
    allowedDependencies = {
      "users",
      "assets",
      "inspections",
      "maintenance",
      "shared",
      "shared::api"
    })
package com.smartdroneinspection.dashboard;
