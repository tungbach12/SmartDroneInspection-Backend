@org.springframework.modulith.ApplicationModule(
    displayName = "Assets",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "users",
      "shared::storage",
      "subscriptions"
    })
package com.smartdroneinspection.assets;
