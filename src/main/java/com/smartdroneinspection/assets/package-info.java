@org.springframework.modulith.ApplicationModule(
    displayName = "Assets",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "users",
      "shared::storage",
      "inspections::reportcontext",
      "assets::reportcontext"
    })
package com.smartdroneinspection.assets;
