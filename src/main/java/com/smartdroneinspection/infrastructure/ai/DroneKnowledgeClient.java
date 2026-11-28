package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.CaseReference;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.GuidelineReference;
import com.smartdroneinspection.inspections.spi.HistoricalCaseAdvisorPort;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI-compatible adapter for the advisory capabilities behind the inspections-owned SPI.
 *
 * <p>Embeddings power case retrieval; the optional chat/vision model drafts a concise note from the
 * finding's evidence image plus the retrieved engineering context. Both are advisory only: a
 * missing endpoint, timeout, authentication error or malformed response degrades to the inspection
 * module's deterministic path instead of failing a report or a recommendation request.
 */
public class DroneKnowledgeClient implements HistoricalCaseAdvisorPort {

  static final int EMBEDDING_DIMENSIONS = 384;

  private static final String EMBEDDINGS_PATH = "/v1/embeddings";
  private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

  private static final String DRAFT_SYSTEM_PROMPT =
      """
      You are a senior infrastructure inspection engineer authoring an official inspection finding \
      note in %s. Write 2 to 3 concise, natural sentences directly in report voice (never write \
      meta-phrases like "Inspector's current note:" or refer to the inspector in the third person). \
      Cover: (1) what is observed and where within the marked region or checkpoint, (2) the \
      localized technical impact, and (3) a concrete next action citing a retrieved standard code \
      (for example TCVN 9343:2012 or EN 1504-9) when relevant. Ground every claim strictly in the \
      photograph, finding label, severity, region, and retrieved context. Incorporate any concrete \
      field observation from the existing note naturally, and ignore placeholders or raw status \
      words such as "published". Never invent measurements or output UUIDs, JSON, or markdown.""";

  private final RestClient client;
  private final String apiKey;
  private final RestClient embeddingClient;
  private final String embeddingApiKey;
  private final String model;
  private final String chatModel;
  private final ObjectMapper objectMapper;

  public DroneKnowledgeClient(RestClient client, String apiKey, String model) {
    this(client, apiKey, null, new ObjectMapper());
  }

  public DroneKnowledgeClient(
      RestClient client, String apiKey, String model, ObjectMapper objectMapper) {
    this(client, apiKey, model, null, objectMapper);
  }

  public DroneKnowledgeClient(
      RestClient client, String apiKey, String model, String chatModel, ObjectMapper objectMapper) {
    this(client, apiKey, client, apiKey, model, chatModel, objectMapper);
  }

  public DroneKnowledgeClient(
      RestClient client,
      String apiKey,
      RestClient embeddingClient,
      String embeddingApiKey,
      String model,
      String chatModel,
      ObjectMapper objectMapper) {
    this.client = client;
    this.apiKey = apiKey;
    this.embeddingClient = embeddingClient == null ? client : embeddingClient;
    this.embeddingApiKey = embeddingApiKey;
    this.model = model;
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<float[]> embed(String text) {
    if (embeddingClient == null || text == null || text.isBlank()) {
      return Optional.empty();
    }
    try {
      Map<String, Object> payload = Map.of("model", model, "input", text);
      String body =
          embeddingClient
              .post()
              .uri(EMBEDDINGS_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> authorize(headers, embeddingApiKey))
              .body(payload)
              .retrieve()
              .body(String.class);
      return parseEmbedding(body);
    } catch (Exception exception) {
      // Connection, timeout, HTTP status and serialization failures all degrade to hybrid matching.
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> draftFindingNote(DraftFindingNoteCommand command) {
    if (client == null || command == null || chatModel == null || chatModel.isBlank()) {
      return Optional.empty();
    }
    try {
      String body =
          client
              .post()
              .uri(CHAT_COMPLETIONS_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> authorize(headers, apiKey))
              .body(draftRequestBody(command))
              .retrieve()
              .body(String.class);
      return parseNote(body);
    } catch (Exception exception) {
      // A drafting outage must never block the Inspector's editor.
      return Optional.empty();
    }
  }

  private static void authorize(HttpHeaders headers, String token) {
    if (token != null && !token.isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
    }
  }

  private Map<String, Object> draftRequestBody(DraftFindingNoteCommand command) {
    String systemPrompt =
        DRAFT_SYSTEM_PROMPT.formatted(vietnamese(command.locale()) ? "Vietnamese" : "English");
    String userPrompt = userPrompt(command);
    Object userContent =
        hasImage(command)
            ? List.of(
                Map.of("type", "text", "text", userPrompt),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUri(command))))
            : userPrompt;
    return Map.of(
        "model",
        chatModel,
        // Compatible endpoints stream server-sent events unless streaming is explicitly disabled.
        "stream",
        false,
        "messages",
        List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userContent)));
  }

  private String userPrompt(DraftFindingNoteCommand command) {
    StringBuilder prompt = new StringBuilder();
    prompt
        .append("Draft the finding note from this context.\n")
        .append("Finding: ")
        .append(supplied(command.defectLabel()))
        .append("; severity: ")
        .append(supplied(command.severity()))
        .append('\n');
    String region = boundingBoxText(command.boundingBox());
    if (region != null) {
      prompt.append("Marked region [xMin, yMin, xMax, yMax]: ").append(region).append('\n');
    }
    prompt
        .append("Asset: ")
        .append(supplied(command.assetLabel()))
        .append(" (")
        .append(supplied(command.assetCategory()))
        .append(")\n");
    if (command.checkpointTitle() != null && !command.checkpointTitle().isBlank()) {
      prompt.append("Checkpoint: ").append(command.checkpointTitle().strip()).append('\n');
    }
    if (command.existingNote() != null
        && !command.existingNote().isBlank()
        && !command.existingNote().strip().equalsIgnoreCase(supplied(command.defectLabel()))) {
      prompt
          .append("Existing field observation to incorporate naturally: ")
          .append(command.existingNote().strip())
          .append('\n');
    }
    if (command.guidelines() != null && !command.guidelines().isEmpty()) {
      prompt.append("Retrieved standards:\n");
      for (GuidelineReference guideline : command.guidelines()) {
        if (guideline == null) {
          continue;
        }
        prompt
            .append("- ")
            .append(supplied(guideline.standardCode()))
            .append(": ")
            .append(supplied(guideline.title()))
            .append(" - ")
            .append(supplied(guideline.guidance()))
            .append('\n');
      }
    }
    if (command.cases() != null && !command.cases().isEmpty()) {
      prompt.append("Retrieved benchmark cases:\n");
      for (CaseReference matchedCase : command.cases()) {
        if (matchedCase == null) {
          continue;
        }
        prompt
            .append("- ")
            .append(supplied(matchedCase.defectLabel()))
            .append(" (")
            .append(supplied(matchedCase.outcome()))
            .append("): ")
            .append(supplied(matchedCase.technicalNotes()))
            .append('\n');
      }
    }
    if (hasImage(command)) {
      prompt.append("Use the attached site photograph as the primary visual evidence.");
    } else {
      prompt.append("No photograph was available; do not claim any visual observation.");
    }
    return prompt.toString();
  }

  private String boundingBoxText(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(raw);
      if (node.hasNonNull("xMin")
          && node.hasNonNull("yMin")
          && node.hasNonNull("xMax")
          && node.hasNonNull("yMax")) {
        return "[%s, %s, %s, %s]"
            .formatted(
                node.get("xMin").asString(),
                node.get("yMin").asString(),
                node.get("xMax").asString(),
                node.get("yMax").asString());
      }
      if (node.hasNonNull("x")
          && node.hasNonNull("y")
          && node.hasNonNull("w")
          && node.hasNonNull("h")) {
        return "[%s, %s, %s, %s]"
            .formatted(
                node.get("x").asString(),
                node.get("y").asString(),
                node.get("w").asString(),
                node.get("h").asString());
      }
    } catch (Exception ignored) {
      // A malformed stored box is simply not described to the model.
    }
    return null;
  }

  private static boolean hasImage(DraftFindingNoteCommand command) {
    return command.imageBytes() != null && command.imageBytes().length > 0;
  }

  private static String dataUri(DraftFindingNoteCommand command) {
    String contentType =
        command.imageContentType() == null || command.imageContentType().isBlank()
            ? "image/jpeg"
            : command.imageContentType().trim();
    return "data:%s;base64,%s"
        .formatted(contentType, Base64.getEncoder().encodeToString(command.imageBytes()));
  }

  private static String supplied(String value) {
    return value == null || value.isBlank() ? "not supplied" : value.strip();
  }

  private static boolean vietnamese(String locale) {
    String tag = locale == null ? "" : locale.trim().toLowerCase(java.util.Locale.ROOT);
    return tag.equals("vi") || tag.startsWith("vi-");
  }

  private Optional<float[]> parseEmbedding(String body) {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    JsonNode data = objectMapper.readTree(body).get("data");
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

  private Optional<String> parseNote(String body) {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    JsonNode choices = objectMapper.readTree(body).get("choices");
    if (choices == null || !choices.isArray() || choices.size() == 0) {
      return Optional.empty();
    }
    JsonNode message = choices.get(0).get("message");
    JsonNode content = message == null ? null : message.get("content");
    if (content == null || content.isNull()) {
      return Optional.empty();
    }
    String note = OpenAiCompatibleInferenceClient.stripCodeFence(content.asString()).strip();
    if (note.isEmpty() || "null".equalsIgnoreCase(note)) {
      return Optional.empty();
    }
    return Optional.of(note);
  }
}
