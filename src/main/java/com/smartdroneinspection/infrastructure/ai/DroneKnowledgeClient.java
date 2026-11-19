package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.inspections.spi.HistoricalCaseAdvisorPort;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI-compatible embeddings adapter for historical case retrieval.
 *
 * <p>Advisory only. A missing endpoint, timeout, authentication error or malformed response yields
 * an empty embedding so the inspection module degrades to deterministic matching instead of failing
 * a report or a recommendation request.
 */
public class DroneKnowledgeClient implements HistoricalCaseAdvisorPort {

  static final int EMBEDDING_DIMENSIONS = 1536;

  private static final String EMBEDDINGS_PATH = "/v1/embeddings";

  private final RestClient client;
  private final String apiKey;
  private final String model;
  private final ObjectMapper objectMapper;

  public DroneKnowledgeClient(RestClient client, String apiKey, String model) {
    this(client, apiKey, model, new ObjectMapper());
  }

  public DroneKnowledgeClient(
      RestClient client, String apiKey, String model, ObjectMapper objectMapper) {
    this.client = client;
    this.apiKey = apiKey;
    this.model = model;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<float[]> embed(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    try {
      Map<String, Object> payload = Map.of("model", model, "input", text);
      String body =
          client
              .post()
              .uri(EMBEDDINGS_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(
                  headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
                    }
                  })
              .body(payload)
              .retrieve()
              .body(String.class);
      return parse(body);
    } catch (Exception exception) {
      // Connection, timeout, HTTP status and serialization failures all degrade to hybrid matching.
      return Optional.empty();
    }
  }

  private Optional<float[]> parse(String body) {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    JsonNode root = objectMapper.readTree(body);
    JsonNode data = root.get("data");
    if (data == null || !data.isArray() || data.size() == 0) {
      return Optional.empty();
    }
    JsonNode values = data.get(0).get("embedding");
    if (values == null || !values.isArray() || values.size() != EMBEDDING_DIMENSIONS) {
      return Optional.empty();
    }
    float[] vector = new float[EMBEDDING_DIMENSIONS];
    for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
      vector[i] = (float) values.get(i).asDouble();
    }
    return Optional.of(vector);
  }
}
