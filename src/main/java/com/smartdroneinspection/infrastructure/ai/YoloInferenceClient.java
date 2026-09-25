package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls the configured YOLO adapter using binary image input and JSON detection output. */
public class YoloInferenceClient implements AiInferencePort {

  private static final ParameterizedTypeReference<List<YoloDetection>> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient client;
  private final String predictPath;

  YoloInferenceClient(RestClient client, String predictPath) {
    this.client = client;
    this.predictPath = predictPath;
  }

  @Override
  public List<Detection> analyze(byte[] image, String contentType) throws IOException {
    try {
      List<YoloDetection> response =
          client
              .post()
              .uri(predictPath)
              .contentType(MediaType.parseMediaType(contentType))
              .body(image)
              .retrieve()
              .body(RESPONSE_TYPE);
      if (response == null) {
        throw new IOException("YOLO service returned an empty response");
      }
      return response.stream()
          .map(
              detection ->
                  new Detection(
                      detection.modelName(),
                      detection.modelVersion(),
                      detection.predictedLabel(),
                      detection.confidence(),
                      detection.boundingBox()))
          .toList();
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new IOException("YOLO inference request failed", exception);
    }
  }

  public record YoloDetection(
      String modelName,
      String modelVersion,
      String predictedLabel,
      BigDecimal confidence,
      String boundingBox) {}
}
