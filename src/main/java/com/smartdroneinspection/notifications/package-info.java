@org.springframework.modulith.ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::exception",
      "maintenance::events",
      "inspections::events",
      "users"
    })
package com.smartdroneinspection.notifications;
