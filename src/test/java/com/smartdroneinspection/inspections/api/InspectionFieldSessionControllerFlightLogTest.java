package com.smartdroneinspection.inspections.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.service.InspectionDroneHubService;
import com.smartdroneinspection.inspections.service.InspectionFieldSessionService;
import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.smartdroneinspection.shared.exception.GlobalExceptionHandler;
import com.smartdroneinspection.shared.exception.ProblemText;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class InspectionFieldSessionControllerFlightLogTest {

  private final InspectionFieldSessionService fieldSessions =
      mock(InspectionFieldSessionService.class);
  private final InspectionDroneHubService droneHub = mock(InspectionDroneHubService.class);
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").subject(inspectorId.toString()).build();
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InspectionFieldSessionController(fieldSessions, droneHub))
            .setControllerAdvice(
                new GlobalExceptionHandler(new ProblemText(new StaticMessageSource())))
            .setValidator(validator)
            .setCustomArgumentResolvers(
                new HandlerMethodArgumentResolver() {
                  @Override
                  public boolean supportsParameter(MethodParameter parameter) {
                    return Jwt.class.isAssignableFrom(parameter.getParameterType());
                  }

                  @Override
                  public Object resolveArgument(
                      MethodParameter parameter,
                      ModelAndViewContainer mavContainer,
                      NativeWebRequest webRequest,
                      WebDataBinderFactory binderFactory) {
                    return jwt;
                  }
                })
            .build();
  }

  @Test
  void listsSessionWithTelemetrySourceAndHasFlightReportFlag() throws Exception {
    FieldSession session =
        new FieldSession(
            inspectionId, FieldSession.IN_PROGRESS, Instant.parse("2026-11-10T10:00:00Z"));
    session.recordFlightReport(
        "IMPORTED_LOG_SRT", null, 45.2, 32.0, null, null, "UNKNOWN", "Imported SRT", "[]");
    when(fieldSessions.listSessions(inspectorId, inspectionId)).thenReturn(List.of(session));

    mockMvc
        .perform(get("/api/v1/inspections/{id}/field-sessions", inspectionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].telemetrySource").value("IMPORTED_LOG_SRT"))
        .andExpect(jsonPath("$.data[0].hasFlightReport").value(true));
  }

  @Test
  void importsLogMultipartAndRejectsEmptyUpload() throws Exception {
    SmartDroneHubPort.FlightReport report =
        new SmartDroneHubPort.FlightReport(
            inspectionId,
            sessionId,
            "IMPORTED_LOG_CSV",
            Instant.parse("2026-11-10T10:00:00Z"),
            Instant.parse("2026-11-10T10:00:10Z"),
            10,
            25.5,
            30.0,
            98,
            95,
            "UNKNOWN",
            "Imported CSV",
            List.of(),
            null);
    when(droneHub.importFlightLog(
            eq(inspectorId), eq(inspectionId), eq(sessionId), eq("flight.csv"), any()))
        .thenReturn(report);

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "flight.csv",
            "text/csv",
            "timestamp,latitude,longitude\n".getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(
            multipart(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/drone-hub/import-log",
                    inspectionId,
                    sessionId)
                .file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.telemetrySource").value("IMPORTED_LOG_CSV"))
        .andExpect(jsonPath("$.data.finalState").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.durationSeconds").value(10));

    MockMultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
    mockMvc
        .perform(
            multipart(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/drone-hub/import-log",
                    inspectionId,
                    sessionId)
                .file(empty))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FLIGHT_LOG_SIZE_INVALID"));
  }

  @Test
  void acceptsValidManualDeclarationAndRejectsInvalidPayload() throws Exception {
    SmartDroneHubPort.FlightReport report =
        new SmartDroneHubPort.FlightReport(
            inspectionId,
            sessionId,
            "MANUAL_DECLARATION",
            null,
            null,
            900,
            null,
            new BigDecimal("40").doubleValue(),
            null,
            null,
            "UNKNOWN",
            "Manual declaration",
            List.of(),
            null);
    when(droneHub.declareManualFlight(
            inspectorId, inspectionId, sessionId, "NO_MAVLINK_SUPPORT", 15, 40.0, "Flown manually"))
        .thenReturn(report);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/drone-hub/manual-declaration",
                    inspectionId,
                    sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reasonCode\":\"NO_MAVLINK_SUPPORT\",\"estimatedDurationMinutes\":15,\"maxAltitudeMeters\":40,\"notes\":\"Flown manually\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.telemetrySource").value("MANUAL_DECLARATION"))
        .andExpect(jsonPath("$.data.distanceFlownMeters").doesNotExist())
        .andExpect(jsonPath("$.data.finalState").value("UNKNOWN"));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/drone-hub/manual-declaration",
                    inspectionId,
                    sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reasonCode\":\"NO_MAVLINK_SUPPORT\",\"estimatedDurationMinutes\":0,\"notes\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
