@org.springframework.modulith.ApplicationModule(
    displayName = "Inspections",
    allowedDependencies = {
      "assets::domain",
      "assets::enums",
      "assets::repository",
      "users",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "shared::storage",
      "shared",
      "subscriptions",
      "inspections::reportcontext",
      "assets::reportcontext"
    })
package com.smartdroneinspection.inspections;
