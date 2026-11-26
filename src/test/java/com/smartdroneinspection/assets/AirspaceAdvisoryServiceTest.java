package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.assets.service.AirspaceAdvisoryService;
import com.smartdroneinspection.assets.service.CustomerAirspacePolicyAdapter;
import com.smartdroneinspection.shared.api.AirspaceZoneInfo;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AirspaceAdvisoryServiceTest {

  private final AirspaceAdvisoryService service = new AirspaceAdvisoryService();

  @Test
  void listsPublishedVietnamNoFlyAndRestrictedAirspaceZones() {
    List<AirspaceZoneResponse> zones = service.listZones();

    assertThat(zones).isNotEmpty();
    assertThat(zones)
        .extracting(AirspaceZoneResponse::zoneType)
        .contains("NO_FLY_ZONE", "RESTRICTED_ZONE");
    assertThat(zones)
        .allSatisfy(
            zone -> {
              assertThat(zone.code()).isNotBlank();
              assertThat(zone.name()).isNotBlank();
              assertThat(zone.radiusMeters()).isPositive();
              assertThat(zone.legalBasis()).isNotBlank();
            });
  }

  @Test
  void classifiesCoordinatesInsideProhibitedCoreAsNoFlyZoneWithMsg28Warning() {
    AirspaceEvaluationResponse result =
        service.evaluate(BigDecimal.valueOf(10.8188), BigDecimal.valueOf(106.6519));

    assertThat(result.classification()).isEqualTo("NO_FLY_ZONE");
    assertThat(result.messageCode()).isEqualTo("MSG28");
    assertThat(result.advisoryMessage())
        .isEqualTo(
            "A possible restriction is indicated. Verify current applicable authority permission; this lookup is not a flight permit.");
    assertThat(result.portalUrl()).isEqualTo("https://cambay.mod.gov.vn");
    assertThat(result.matchingZones())
        .anySatisfy(zone -> assertThat(zone.zoneType()).isEqualTo("NO_FLY_ZONE"));
  }

  @Test
  void classifiesThuThiemBridgeCoordinatesAsRestrictedAirspaceWithMsg28Warning() {
    AirspaceEvaluationResponse result =
        service.evaluate(BigDecimal.valueOf(10.7789), BigDecimal.valueOf(106.7082));

    assertThat(result.classification()).isEqualTo("RESTRICTED_ZONE");
    assertThat(result.messageCode()).isEqualTo("MSG28");
    assertThat(result.advisoryMessage())
        .isEqualTo(
            "A possible restriction is indicated. Verify current applicable authority permission; this lookup is not a flight permit.");
    assertThat(result.matchingZones()).isNotEmpty();
    assertThat(result.matchingZones()).allMatch(zone -> "RESTRICTED_ZONE".equals(zone.zoneType()));
  }

  @Test
  void classifiesUnrestrictedCoordinatesAsClearZone() {
    AirspaceEvaluationResponse result =
        service.evaluate(BigDecimal.valueOf(10.5500), BigDecimal.valueOf(106.8500));

    assertThat(result.classification()).isEqualTo("CLEAR_ZONE");
    assertThat(result.messageCode()).isEqualTo("AIRSPACE_CLEAR");
    assertThat(result.matchingZones()).isEmpty();
  }

  @Test
  void rejectsOutOfRangeCoordinates() {
    assertThatThrownBy(
            () -> service.evaluate(BigDecimal.valueOf(95.0), BigDecimal.valueOf(106.7082)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Latitude must be between -90 and 90");
  }

  @Test
  void adapterExposesZoneCeilingAndAuthorityForRestrictedZone() {
    CustomerAirspacePolicyAdapter adapter = new CustomerAirspacePolicyAdapter(service);

    AirspaceZoneInfo info =
        adapter.evaluateZone(BigDecimal.valueOf(10.7789), BigDecimal.valueOf(106.7082));

    assertThat(info.status()).isEqualTo(CustomerAirspaceStatus.RESTRICTED_ZONE);
    assertThat(info.zoneCode()).isEqualTo("VN-RES-SGN-SAIGON-RIVER");
    assertThat(info.maxAltitudeMeters()).isEqualTo(120);
    assertThat(info.authority()).isNotBlank();
  }

  @Test
  void detectsFootprintPerimeterOverlapIntoAdjacentNoFlyZone() {
    // 10.8188 + 0.0320 deg lat is ~3,558 m north of Tan Son Nhat core (radius 3,500 m -> ~58 m
    // outside boundary).
    BigDecimal boundaryLat = BigDecimal.valueOf(10.8508);
    BigDecimal boundaryLon = BigDecimal.valueOf(106.6519);

    assertThat(service.evaluate(boundaryLat, boundaryLon).classification())
        .isNotEqualTo("NO_FLY_ZONE");
    // 1,000 m^2 -> ~17.8 m radius (stays outside the 3,500 m core)
    assertThat(
            service.findFootprintNoFlyOverlap(boundaryLat, boundaryLon, BigDecimal.valueOf(1000)))
        .isEmpty();
    // 100,000 m^2 -> ~178.4 m radius (crosses ~58 m buffer into Tan Son Nhat NO_FLY_ZONE)
    assertThat(
            service.findFootprintNoFlyOverlap(
                boundaryLat, boundaryLon, BigDecimal.valueOf(100_000)))
        .isPresent()
        .get()
        .satisfies(zone -> assertThat(zone.code()).isEqualTo("VN-NFZ-SGN-TSN-CORE"));
  }
}
