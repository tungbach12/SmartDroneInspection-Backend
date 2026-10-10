@org.springframework.modulith.ApplicationModule(
    displayName = "Assets",
    allowedDependencies = {
      "shared",
      "shared::api",
      "shared::auth",
      "shared::exception",
      "users",
      "shared::storage",
      "subscriptions",
      // MF1-07 records a permit's source evidence reference, so assets asks the inspections module
      // the single ownership question its EvidenceOwnership facade answers.
      "inspections"
    })
package com.smartdroneinspection.assets;
