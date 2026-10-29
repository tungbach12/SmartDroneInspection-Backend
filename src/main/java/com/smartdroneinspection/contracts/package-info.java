@org.springframework.modulith.ApplicationModule(
    displayName = "Contracts",
    allowedDependencies = {
      "assets::domain",
      "assets::enums",
      "assets::repository",
      "users",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "shared::storage",
      "inspections::spi"
    })
package com.smartdroneinspection.contracts;
