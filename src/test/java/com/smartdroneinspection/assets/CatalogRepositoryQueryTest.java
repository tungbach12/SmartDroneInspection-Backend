package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CatalogRepositoryQueryTest {

  @Autowired DroneRepository drones;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired UserRepository users;

  AssetTestFixture.Data data;

  @BeforeEach
  void setUp() {
    data = new AssetTestFixture(categories, assets, users).create();
  }

  @Test
  void assignableDroneLookupReturnsOnlyActiveDronesOfTheOwner() {
    Drone active = drones.saveAndFlush(new Drone(data.clientId(), "DJI-M350-RTK-T01"));
    Drone heldForMaintenance = drones.saveAndFlush(new Drone(data.clientId(), "DJI-M350-RTK-T02"));
    heldForMaintenance.changeServiceability(DroneServiceability.MAINTENANCE);
    drones.saveAndFlush(heldForMaintenance);

    assertThat(drones.findAssignableByOwnerCustomerId(data.clientId())).containsExactly(active);
    assertThat(drones.findAssignableByOwnerCustomerId(data.otherClientId())).isEmpty();
    assertThat(drones.findByIdAndOwnerCustomerId(active.getId(), data.otherClientId())).isEmpty();
  }
}
