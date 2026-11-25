package com.smartdroneinspection.assets;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public assets-module boundary for querying partner drone registration expirations. */
public interface DroneExpiryAccess {

  List<PartnerDroneRegistration> listActivePartnerDroneRegistrations();

  record PartnerDroneRegistration(
      UUID droneId,
      UUID inspectorOwnerId,
      String serialNumber,
      String model,
      String registrationNumber,
      Instant registrationExpiresAt) {}
}
