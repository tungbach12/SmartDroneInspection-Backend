package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/** Outbound port for advisory image analysis; results are never official findings by themselves. */
public interface AiInferencePort {

  List<Detection> analyze(byte[] image, String contentType) throws IOException;

  record Detection(
      String modelName,
      String modelVersion,
      String predictedLabel,
      BigDecimal confidence,
      String boundingBox) {}
}
