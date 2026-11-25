@org.springframework.modulith.ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::exception",
      "assets",
      "assets::events",
      "contracts::events",
      "inspections::events",
      "users",
      "users::events"
    })
package com.smartdroneinspection.notifications;
