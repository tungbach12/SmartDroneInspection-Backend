package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.assets.service.AirspaceAdvisoryService;
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
}
