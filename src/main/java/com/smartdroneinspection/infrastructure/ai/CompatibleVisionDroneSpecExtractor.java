package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.infrastructure.ai.OpenAiCompatibleInferenceClient.ChatCompletionResponse;
import com.smartdroneinspection.shared.api.DroneSpecVisionPort;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Extracts structured drone specifications and Vietnamese/English UAV registration fields using an
 * OpenAI-compatible vision chat model.
 */
public class CompatibleVisionDroneSpecExtractor implements DroneSpecVisionPort {

  private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
  private static final Set<String> VALID_FEATURES =
      Set.of(
          "RTK_POSITIONING",
          "THERMAL_CAMERA",
          "LIDAR_SCANNER",
          "NIGHT_VISION",
          "OBSTACLE_AVOIDANCE",
          "FAILSAFE_RTH",
          "WEATHERPROOF_IP",
          "PARACHUTE_SAFETY",
          "UPWARD_GIMBAL",
          "GNSS_DENIED_POSITIONING");

  private static final String EXTRACTION_INSTRUCTIONS =
      """
      You extract structured drone (UAV) registration and technical specification fields from images
      such as Vietnamese aviation registration certificates, airframe spec sheets, or product labels.
      Reply with ONE valid JSON object and nothing else:
      {
        "model": string or null,
        "manufacturer": string or null,
        "serialNumber": string or null,
        "registrationNumber": string or null,
        "registrationExpiresAt": "YYYY-MM-DD" or null,
        "mtowKg": number or null,
        "maxFlightTimeMinutes": integer or null,
        "maxWindResistanceMps": number or null,
        "maxAltitudeMeters": number or null,
        "opticalZoomLevel": string or null,
        "cameraSensor": string or null,
        "features": array of matching codes from ["RTK_POSITIONING","THERMAL_CAMERA","LIDAR_SCANNER","NIGHT_VISION","OBSTACLE_AVOIDANCE","FAILSAFE_RTH","WEATHERPROOF_IP","PARACHUTE_SAFETY","UPWARD_GIMBAL","GNSS_DENIED_POSITIONING"]
      }
      Strict rules:
      1. NEVER guess or fabricate any value not legible in the image or text hint; return null instead.
      2. Convert weights in grams to kilograms (e.g. 249 g -> 0.249).
      3. Interpret numeric Vietnamese dates as DD/MM/YYYY and normalize to YYYY-MM-DD.
      4. Format opticalZoomLevel without the trailing 'x' when numeric (e.g. "23" for 23x).""";

  private final RestClient client;
  private final String apiKey;
  private final String model;
  private final ObjectMapper objectMapper;

  CompatibleVisionDroneSpecExtractor(
      RestClient client, String apiKey, String model, ObjectMapper objectMapper) {
    this.client = client;
    this.apiKey = apiKey;
    this.model = model;
    this.objectMapper = objectMapper;
  }

  @Override
  public ExtractedSpecs extract(byte[] imageBytes, String contentType, String pdfTextHint)
      throws IOException {
    String content;
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
              .body(requestBody(imageBytes, contentType, pdfTextHint))
              .retrieve()
              .body(ChatCompletionResponse.class);
      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().getFirst() == null
          || response.choices().getFirst().message() == null
          || response.choices().getFirst().message().content() == null) {
        throw new IOException("Vision extraction endpoint returned an empty response");
      }
      content = response.choices().getFirst().message().content();
    } catch (RestClientException | IllegalArgumentException ex) {
      throw new IOException("Vision spec extraction request failed", ex);
    }

    return parseExtractedSpecs(content);
  }

  private Map<String, Object> requestBody(
      byte[] imageBytes, String contentType, String pdfTextHint) {
    String dataUri =
        "data:%s;base64,%s".formatted(contentType, Base64.getEncoder().encodeToString(imageBytes));
    String promptText =
        pdfTextHint != null && !pdfTextHint.isBlank()
            ? "Extract visible drone registration and specification fields. Extracted document text hint:\n"
                + pdfTextHint
            : "Extract visible drone registration and specification fields from this image.";
    return Map.of(
        "model",
        model,
        "stream",
        false,
        "messages",
        List.of(
            Map.of("role", "system", "content", EXTRACTION_INSTRUCTIONS),
            Map.of(
                "role",
                "user",
                "content",
                List.of(
                    Map.of("type", "text", "text", promptText),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));
  }

  private ExtractedSpecs parseExtractedSpecs(String content) throws IOException {
    String cleaned = OpenAiCompatibleInferenceClient.stripCodeFence(content);
    JsonNode root;
    try {
      root = objectMapper.readTree(cleaned);
    } catch (RuntimeException ex) {
      throw new IOException("Vision extraction did not return valid JSON", ex);
    }
    return new ExtractedSpecs(
        cleanText(root.get("model")),
        cleanText(root.get("manufacturer")),
        cleanText(root.get("serialNumber")),
        cleanText(root.get("registrationNumber")),
        parseDate(cleanText(root.get("registrationExpiresAt"))),
        parseDecimal(root.get("mtowKg")),
        parseInt(root.get("maxFlightTimeMinutes")),
        parseDecimal(root.get("maxWindResistanceMps")),
        parseDecimal(root.get("maxAltitudeMeters")),
        normalizeZoom(cleanText(root.get("opticalZoomLevel"))),
        cleanText(root.get("cameraSensor")),
        parseFeatures(root.get("features")));
  }

  private static String cleanText(JsonNode node) {
    if (node == null || node.isNull()) return null;
    String value = node.asString();
    if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) return null;
    return value.trim();
  }

  private static BigDecimal parseDecimal(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (node.isNumber()) {
      BigDecimal val = node.decimalValue();
      return val.signum() > 0 ? val : null;
    }
    String raw = cleanText(node);
    if (raw == null) return null;
    String cleaned = raw.replaceAll("[^0-9.,]", "").replace(',', '.');
    if (cleaned.isBlank()) return null;
    try {
      BigDecimal val = new BigDecimal(cleaned);
      return val.signum() > 0 ? val : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Integer parseInt(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (node.isNumber()) {
      int val = node.intValue();
      return val > 0 ? val : null;
    }
    String raw = cleanText(node);
    if (raw == null) return null;
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.isBlank()) return null;
    try {
      int val = Integer.parseInt(digits);
      return val > 0 ? val : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null) return null;
    for (DateTimeFormatter fmt :
        List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"))) {
      try {
        return LocalDate.parse(raw, fmt);
      } catch (DateTimeParseException ignored) {
        // try next
      }
    }
    return null;
  }

  private static String normalizeZoom(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim().replaceAll("(?i)x$", "");
    return trimmed.isBlank() ? null : trimmed;
  }

  private static List<String> parseFeatures(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> out = new ArrayList<>();
    for (JsonNode item : node) {
      String code = cleanText(item);
      if (code != null) {
        String normalized = code.toUpperCase(Locale.ROOT);
        if (VALID_FEATURES.contains(normalized) && !out.contains(normalized)) {
          out.add(normalized);
        }
      }
    }
    return List.copyOf(out);
  }
}
