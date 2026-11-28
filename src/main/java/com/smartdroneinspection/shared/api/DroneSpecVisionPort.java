package com.smartdroneinspection.shared.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Advisory port for extracting airframe specifications and registration details from an uploaded
 * image or rendered document page. Outputs pre-fill a draft form and never bypass human review.
 */
public interface DroneSpecVisionPort {

  ExtractedSpecs extract(byte[] imageBytes, String contentType, String pdfTextHint)
      throws IOException;

  record ExtractedSpecs(
      String model,
      String manufacturer,
      String serialNumber,
      String registrationNumber,
      LocalDate registrationExpiresAt,
      BigDecimal mtowKg,
      Integer maxFlightTimeMinutes,
      BigDecimal maxWindResistanceMps,
      BigDecimal maxAltitudeMeters,
      String opticalZoomLevel,
      String cameraSensor,
      List<String> features) {}
}
