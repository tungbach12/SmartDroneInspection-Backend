package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.shared.api.AirspaceZoneAdvisor;
import com.smartdroneinspection.shared.api.AirspaceZoneInfo;
import com.smartdroneinspection.shared.api.CustomerAirspaceEvaluation;
import com.smartdroneinspection.shared.api.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CustomerAirspacePolicyAdapter implements CustomerAirspacePolicy, AirspaceZoneAdvisor {

  private final AirspaceAdvisoryService advisory;

  public CustomerAirspacePolicyAdapter(AirspaceAdvisoryService advisory) {
    this.advisory = advisory;
  }

  @Override
  public CustomerAirspaceEvaluation evaluateCustomerOnboarding(
      BigDecimal latitude, BigDecimal longitude) {
    try {
      AirspaceEvaluationResponse response = advisory.evaluate(latitude, longitude);
      CustomerAirspaceStatus status =
          switch (response.classification()) {
            case "CLEAR_ZONE" ->
                response.nearbyZones().isEmpty()
                    ? CustomerAirspaceStatus.CLEAR_ZONE
                    : CustomerAirspaceStatus.UNKNOWN;
            case "RESTRICTED_ZONE" -> CustomerAirspaceStatus.RESTRICTED_ZONE;
            case "NO_FLY_ZONE" -> CustomerAirspaceStatus.NO_FLY_ZONE;
            default -> CustomerAirspaceStatus.UNKNOWN;
          };
      return new CustomerAirspaceEvaluation(status, response.advisoryMessage());
    } catch (RuntimeException exception) {
      return new CustomerAirspaceEvaluation(
          CustomerAirspaceStatus.ERROR, "Airspace assessment is temporarily unavailable.");
    }
  }

  @Override
  public AirspaceZoneInfo evaluateZone(BigDecimal latitude, BigDecimal longitude) {
    if (latitude == null || longitude == null) {
      return new AirspaceZoneInfo(CustomerAirspaceStatus.UNKNOWN, null, null, null, null, null);
    }
    try {
      AirspaceEvaluationResponse response = advisory.evaluate(latitude, longitude);
      CustomerAirspaceStatus status =
          switch (response.classification()) {
            case "CLEAR_ZONE" -> CustomerAirspaceStatus.CLEAR_ZONE;
            case "RESTRICTED_ZONE" -> CustomerAirspaceStatus.RESTRICTED_ZONE;
            case "NO_FLY_ZONE" -> CustomerAirspaceStatus.NO_FLY_ZONE;
            default -> CustomerAirspaceStatus.UNKNOWN;
          };
      AirspaceZoneResponse primary =
          response.matchingZones().isEmpty() ? null : response.matchingZones().getFirst();
      return new AirspaceZoneInfo(
          status,
          primary != null ? primary.code() : null,
          primary != null ? primary.name() : null,
          primary != null ? primary.authority() : null,
          primary != null ? primary.maxAltitudeMetersAgl() : null,
          response.advisoryMessage());
    } catch (RuntimeException exception) {
      return new AirspaceZoneInfo(
          CustomerAirspaceStatus.ERROR,
          null,
          null,
          null,
          null,
          "Airspace assessment is temporarily unavailable.");
    }
  }
}
