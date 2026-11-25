package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.DroneExpiryAccess;
import com.smartdroneinspection.assets.repository.DroneRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DroneExpiryAccessService implements DroneExpiryAccess {

  private final DroneRepository drones;

  public DroneExpiryAccessService(DroneRepository drones) {
    this.drones = drones;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PartnerDroneRegistration> listActivePartnerDroneRegistrations() {
    return drones.findActivePartnerDrones().stream()
        .filter(d -> d.getInspectorOwnerId() != null && d.getRegistrationExpiresAt() != null)
        .map(
            d ->
                new PartnerDroneRegistration(
                    d.getId(),
                    d.getInspectorOwnerId(),
                    d.getSerialNumber(),
                    d.getModel(),
                    d.getRegistrationNumber(),
                    d.getRegistrationExpiresAt()))
        .toList();
  }
}
