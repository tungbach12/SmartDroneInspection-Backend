package com.smartdroneinspection.inspections.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.inspections.api.dto.request.CreateRecurringScheduleRequest;
import com.smartdroneinspection.inspections.api.dto.response.RecurringScheduleDetailResponse;
import com.smartdroneinspection.inspections.api.dto.response.RecurringSchedulePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.RecurringScheduleResponse;
import com.smartdroneinspection.inspections.api.dto.response.ScheduleOccurrenceResponse;
import com.smartdroneinspection.inspections.service.RecurringInspectionScheduleService;
import com.smartdroneinspection.shared.exception.GlobalExceptionHandler;
import com.smartdroneinspection.shared.exception.ProblemText;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class InspectionScheduleControllerTest {

  private RecurringInspectionScheduleService scheduleService;
  private MockMvc mockMvc;

  private final UUID actorId = UUID.randomUUID();
  private final UUID scheduleId = UUID.randomUUID();
  private final UUID occurrenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    scheduleService = mock(RecurringInspectionScheduleService.class);

    Jwt mockJwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(actorId.toString())
            .claim("roles", List.of("ORG_MANAGER"))
            .build();

    HandlerMethodArgumentResolver jwtResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return mockJwt;
          }
        };

    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");

    mockMvc =
        MockMvcBuilders.standaloneSetup(new InspectionScheduleController(scheduleService))
            .setCustomArgumentResolvers(jwtResolver)
            .setControllerAdvice(new GlobalExceptionHandler(new ProblemText(messageSource)))
            .build();
  }

  @Test
  void createScheduleReturns201AndApiResponse() throws Exception {
    RecurringScheduleResponse response = mockResponse(scheduleId);
    when(scheduleService.create(eq(actorId), any(CreateRecurringScheduleRequest.class)))
        .thenReturn(response);

    String payload =
        """
        {
          "assetId": "%s",
          "anchor": "2026-11-01T09:00:00",
          "timezone": "Asia/Ho_Chi_Minh",
          "unit": "MONTH",
          "interval": 1,
          "durationSeconds": 7200,
          "reminderDays": 3
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/inspection-schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(scheduleId.toString()))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  void getScheduleReturns200AndDetailResponse() throws Exception {
    RecurringScheduleDetailResponse detail =
        new RecurringScheduleDetailResponse(
            scheduleId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge A",
            "Zone 1",
            actorId,
            "Manager",
            UUID.randomUUID(),
            "Inspector",
            UUID.randomUUID(),
            "DRONE-1",
            "M300",
            LocalDateTime.parse("2026-11-01T09:00:00"),
            "UTC",
            "MONTH",
            1,
            7200L,
            3,
            null,
            "ACTIVE",
            0L,
            Instant.parse("2026-11-01T09:00:00Z"),
            1L,
            0L,
            Instant.now(),
            Instant.now(),
            Collections.emptyList());

    when(scheduleService.get(actorId, scheduleId)).thenReturn(detail);

    mockMvc
        .perform(get("/api/v1/inspection-schedules/{scheduleId}", scheduleId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(scheduleId.toString()))
        .andExpect(jsonPath("$.data.occurrences").isArray());
  }

  @Test
  void listSchedulesReturnsPagedResponse() throws Exception {
    RecurringScheduleResponse item = mockResponse(scheduleId);
    RecurringSchedulePageResponse page =
        new RecurringSchedulePageResponse(List.of(item), 1, 20, 1L, 1);

    when(scheduleService.list(eq(actorId), any(), any(), eq(1), eq(20))).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/inspection-schedules?page=1&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].id").value(scheduleId.toString()))
        .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  void pauseScheduleReturnsUpdatedResponse() throws Exception {
    RecurringScheduleResponse response = mockResponse(scheduleId);
    when(scheduleService.pause(actorId, scheduleId)).thenReturn(response);

    mockMvc
        .perform(post("/api/v1/inspection-schedules/{scheduleId}/pause", scheduleId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(scheduleService).pause(actorId, scheduleId);
  }

  @Test
  void resumeScheduleReturnsUpdatedResponse() throws Exception {
    RecurringScheduleResponse response = mockResponse(scheduleId);
    when(scheduleService.resume(actorId, scheduleId)).thenReturn(response);

    mockMvc
        .perform(post("/api/v1/inspection-schedules/{scheduleId}/resume", scheduleId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(scheduleService).resume(actorId, scheduleId);
  }

  @Test
  void endScheduleReturnsUpdatedResponse() throws Exception {
    RecurringScheduleResponse response = mockResponse(scheduleId);
    when(scheduleService.end(actorId, scheduleId)).thenReturn(response);

    mockMvc
        .perform(post("/api/v1/inspection-schedules/{scheduleId}/end", scheduleId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(scheduleService).end(actorId, scheduleId);
  }

  @Test
  void retryOccurrenceReturnsUpdatedOccurrence() throws Exception {
    ScheduleOccurrenceResponse occResponse =
        new ScheduleOccurrenceResponse(
            occurrenceId,
            scheduleId,
            0L,
            "key-0",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "GENERATED",
            UUID.randomUUID(),
            null,
            1,
            Instant.now(),
            Instant.now());

    when(scheduleService.retryOccurrence(actorId, scheduleId, occurrenceId))
        .thenReturn(occResponse);

    mockMvc
        .perform(
            post(
                "/api/v1/inspection-schedules/{scheduleId}/occurrences/{occurrenceId}/retry",
                scheduleId,
                occurrenceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("GENERATED"));
  }

  @Test
  void skipOccurrenceReturnsUpdatedOccurrence() throws Exception {
    ScheduleOccurrenceResponse occResponse =
        new ScheduleOccurrenceResponse(
            occurrenceId,
            scheduleId,
            0L,
            "key-0",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "SKIPPED",
            null,
            "Bad weather",
            0,
            Instant.now(),
            Instant.now());

    when(scheduleService.skipOccurrence(eq(actorId), eq(scheduleId), eq(occurrenceId), any()))
        .thenReturn(occResponse);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspection-schedules/{scheduleId}/occurrences/{occurrenceId}/skip",
                    scheduleId,
                    occurrenceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Bad weather\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("SKIPPED"));
  }

  private RecurringScheduleResponse mockResponse(UUID id) {
    return new RecurringScheduleResponse(
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Asset Name",
        "Location",
        actorId,
        "Creator",
        UUID.randomUUID(),
        "Inspector",
        UUID.randomUUID(),
        "DRONE-123",
        "Matrice 300",
        LocalDateTime.parse("2026-11-01T09:00:00"),
        "UTC",
        "MONTH",
        1,
        7200L,
        3,
        null,
        "ACTIVE",
        0L,
        Instant.parse("2026-11-01T09:00:00Z"),
        0L,
        0L,
        Instant.now(),
        Instant.now());
  }
}
