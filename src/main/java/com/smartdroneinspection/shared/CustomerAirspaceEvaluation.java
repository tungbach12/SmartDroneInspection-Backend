package com.smartdroneinspection.shared;

public record CustomerAirspaceEvaluation(CustomerAirspaceStatus status, String message) {
  public CustomerAirspaceEvaluation {
    if (status == null) {
      status = CustomerAirspaceStatus.UNKNOWN;
    }
  }
}
