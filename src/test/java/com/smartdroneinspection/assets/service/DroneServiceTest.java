package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class DroneServiceTest {

  private DroneRepository drones;
  private UserAccess userAccess;
  private DroneService service;

  private final UUID customerId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    drones = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    service = new DroneService(drones, userAccess);

    when(userAccess.findActiveUser(customerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(customerId, Set.of(Roles.CUSTOMER), "Customer")));
    when(userAccess.findActiveUser(managerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), "Manager")));
  }

  @Test
  void listPagedFiltersByRoleScopeServiceabilityAndSearch() {
    Drone customerDrone =
        new Drone(
            customerId,
            "SN-CUST-1",
            "Mavic 3",
            "DJI",
            null,
            DroneServiceability.ACTIVE,
            null,
            "REG-1",
            "reg.pdf",
            java.time.Instant.now().plusSeconds(86400));

    when(drones.searchByOwnerCustomerIdAndServiceability(
            eq(customerId), eq(DroneServiceability.ACTIVE), eq("CUST"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(customerDrone), PageRequest.of(0, 10), 1));

    var customerPage = service.listPaged(customerId, 1, 10, " CUST ", DroneServiceability.ACTIVE);
    assertThat(customerPage.totalCount()).isEqualTo(1);
    assertThat(customerPage.items())
        .singleElement()
        .satisfies(d -> assertThat(d.serialNumber()).isEqualTo("SN-CUST-1"));

    when(drones.findByServiceability(eq(DroneServiceability.MAINTENANCE), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(customerDrone), PageRequest.of(1, 5), 6));

    var managerPage = service.listPaged(managerId, 2, 5, null, DroneServiceability.MAINTENANCE);
    assertThat(managerPage.page()).isEqualTo(2);
    assertThat(managerPage.pageSize()).isEqualTo(5);
    assertThat(managerPage.totalCount()).isEqualTo(6);
    assertThat(managerPage.totalPages()).isEqualTo(2);
  }
}
