@org.springframework.modulith.ApplicationModule(
    displayName = "Inspections",
    allowedDependencies = {
      "inspectionrequests::domain",
      "inspectionrequests::enums",
      "inspectionrequests::repository",
      "assets::domain",
      "assets::enums",
      "assets::repository",
      "users",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "shared::storage"
    })
package com.smartdroneinspection.inspections;
