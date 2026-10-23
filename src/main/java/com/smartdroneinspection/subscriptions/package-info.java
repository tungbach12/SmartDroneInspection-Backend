@org.springframework.modulith.ApplicationModule(
    displayName = "Subscriptions",
    allowedDependencies = {
      "shared::api",
      "shared::auth",
      "shared::exception",
      "users",
      "subscriptions::gateway"
    })
package com.smartdroneinspection.subscriptions;
