@org.springframework.modulith.ApplicationModule(
    displayName = "Users",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::auth",
      "shared::config",
      "shared::exception",
      "shared::storage"
    })
package com.smartdroneinspection.users;
