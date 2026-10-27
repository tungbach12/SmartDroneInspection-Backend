@org.springframework.modulith.ApplicationModule(
    displayName = "Workforce",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "users",
      "subscriptions"
    })
package com.smartdroneinspection.workforce;
