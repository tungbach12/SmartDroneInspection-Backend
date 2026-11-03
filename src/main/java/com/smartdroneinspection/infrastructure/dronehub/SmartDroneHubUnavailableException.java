package com.smartdroneinspection.infrastructure.dronehub;

import com.smartdroneinspection.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SmartDroneHubUnavailableException extends BusinessException {
  public SmartDroneHubUnavailableException(String message) {
    super(HttpStatus.BAD_GATEWAY, "SMARTDRONEHUB_UNAVAILABLE", message);
  }

  public SmartDroneHubUnavailableException(String message, Throwable cause) {
    super(HttpStatus.BAD_GATEWAY, "SMARTDRONEHUB_UNAVAILABLE", message);
    initCause(cause);
  }
}
