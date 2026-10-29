package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.shared.CustomerAirspaceEvaluation;
import com.smartdroneinspection.shared.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.CustomerAirspaceStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CustomerAirspacePolicyAdapter implements CustomerAirspacePolicy {

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
}
