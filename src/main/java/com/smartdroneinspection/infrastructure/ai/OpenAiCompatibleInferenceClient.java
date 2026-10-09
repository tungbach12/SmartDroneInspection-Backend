package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Advisory image detection through an OpenAI-compatible chat endpoint. The model returns candidate
 * detections only; a human still decides whether any of them becomes a finding.
 */
public class OpenAiCompatibleInferenceClient implements AiInferencePort {

  private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
  private static final String DETECTION_INSTRUCTIONS =
      """
      You inspect infrastructure inspection photographs for visible defects.
      Reply with one JSON object and nothing else:
      {"detections":[{"predictedLabel":"short defect name","confidence":0.0,"boundingBox":{"xMin":0.0,"yMin":0.0,"xMax":1.0,"yMax":1.0}}]}
      Box values are normalized 0 to 1. Use an empty detections array when no defect is visible or
      the image is too small or unclear to judge. Never infer hidden or structural conditions.""";

  private final RestClient client;
  private final String apiKey;
  private final String model;
  private final ObjectMapper objectMapper;

  OpenAiCompatibleInferenceClient(
      RestClient client, String apiKey, String model, ObjectMapper objectMapper) {
    this.client = client;
    this.apiKey = apiKey;
    this.model = model;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Detection> analyze(byte[] image, String contentType) throws IOException {
    String content;
    String responseModel;
    try {
      ChatCompletionResponse response =
          client
              .post()
              .uri(CHAT_COMPLETIONS_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(
                  headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
                    }
                  })
              .body(requestBody(image, contentType))
              .retrieve()
              .body(ChatCompletionResponse.class);
      if (response == null || response.choices() == null || response.choices().isEmpty()) {
        throw new IOException("Inference service returned an empty response");
      }
      ChatCompletionResponse.Choice choice = response.choices().getFirst();
      if (choice == null || choice.message() == null || choice.message().content() == null) {
        throw new IOException("Inference service returned an empty response");
      }
      content = choice.message().content();
      responseModel = response.model();
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new IOException("Image inference request failed", exception);
    }

    return toDetections(content, responseModel);
  }

  private Map<String, Object> requestBody(byte[] image, String contentType) {
    String dataUri =
        "data:%s;base64,%s".formatted(contentType, Base64.getEncoder().encodeToString(image));
    return Map.of(
        "model",
        model,
        // Compatible endpoints stream server-sent events unless streaming is explicitly disabled.
        "stream",
        false,
        "messages",
        List.of(
            Map.of("role", "system", "content", DETECTION_INSTRUCTIONS),
            Map.of(
                "role",
                "user",
                "content",
                List.of(
                    Map.of("type", "text", "text", "List the visible defects in this photograph."),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));
  }

  private List<Detection> toDetections(String content, String responseModel) throws IOException {
    JsonNode payload = parsePayload(content);
    JsonNode detections = payload.get("detections");
    if (detections == null || !detections.isArray()) {
      throw new IOException("Inference service did not return a detection payload");
    }

    List<Detection> results = new java.util.ArrayList<>();
    for (JsonNode node : detections) {
      String label = text(node, "predictedLabel");
      JsonNode box = node.get("boundingBox");
      if (label == null || label.isBlank() || box == null || !box.isObject()) {
        throw new IOException("Inference service returned a detection without a label and box");
      }
      BigDecimal confidence = confidence(node.get("confidence"));
      validateBoundingBox(box);
      results.add(
          new Detection(
              model,
              responseModel == null || responseModel.isBlank() ? model : responseModel,
              label.trim(),
              confidence,
              box.toString()));
    }
    return List.copyOf(results);
  }

  private JsonNode parsePayload(String content) throws IOException {
    String text = stripCodeFence(content);
    try {
      return objectMapper.readTree(text);
    } catch (RuntimeException exception) {
      throw new IOException("Inference service did not return a detection payload", exception);
    }
  }

  /** Models often wrap JSON in a fenced block even when told not to, so the fence is tolerated. */
  private static String stripCodeFence(String content) {
    String trimmed = content.strip();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    int firstNewline = trimmed.indexOf('\n');
    int closingFence = trimmed.lastIndexOf("```");
    if (firstNewline < 0 || closingFence <= firstNewline) {
      return trimmed;
    }
    return trimmed.substring(firstNewline + 1, closingFence).strip();
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }

  private static BigDecimal confidence(JsonNode node) throws IOException {
    if (node == null || !node.isNumber()) {
      throw new IOException("Inference service returned a detection without a numeric confidence");
    }
    BigDecimal value = node.decimalValue();
    if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
      throw new IOException("Inference service returned a confidence outside 0 to 1");
    }
    return value;
  }

  private static void validateBoundingBox(JsonNode box) throws IOException {
    BigDecimal xMin = coordinate(box, "xMin");
    BigDecimal yMin = coordinate(box, "yMin");
    BigDecimal xMax = coordinate(box, "xMax");
    BigDecimal yMax = coordinate(box, "yMax");
    if (xMin.compareTo(xMax) >= 0 || yMin.compareTo(yMax) >= 0) {
      throw new IOException("Inference service returned an invalid bounding box range");
    }
  }

  private static BigDecimal coordinate(JsonNode box, String field) throws IOException {
    JsonNode value = box.get(field);
    if (value == null || !value.isNumber()) {
      throw new IOException(
          "Inference service returned a bounding box without numeric coordinates");
    }
    BigDecimal coordinate = value.decimalValue();
    if (coordinate.signum() < 0 || coordinate.compareTo(BigDecimal.ONE) > 0) {
      throw new IOException("Inference service returned a bounding box outside 0 to 1");
    }
    return coordinate;
  }

  public record ChatCompletionResponse(String model, List<Choice> choices) {
    public record Choice(Message message) {}

    public record Message(String content) {}
  }
}
