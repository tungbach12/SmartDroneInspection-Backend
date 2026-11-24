package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectorRatingApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionRepository inspections;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired jakarta.persistence.EntityManager entityManager;

  private MockMvc mockMvc;
  private UUID customerId;
  private UUID managerId;
  private UUID inspectorId;
  private UUID adminId;
  private UUID completedInspectionId;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    User customer =
        new User(
            "cust-" + UUID.randomUUID() + "@example.test", "Customer Co", null, UserStatus.ACTIVE);
    customer.addRole(UserRole.CUSTOMER);
    customerId = users.saveAndFlush(customer).getId();

    User manager =
        new User(
            "mgr-" + UUID.randomUUID() + "@example.test", "Manager One", null, UserStatus.ACTIVE);
    manager.addRole(UserRole.MANAGER);
    managerId = users.saveAndFlush(manager).getId();

    User inspector =
        new User(
            "insp-" + UUID.randomUUID() + "@example.test",
            "Inspector One",
            null,
            UserStatus.ACTIVE);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();

    User admin =
        new User(
            "adm-" + UUID.randomUUID() + "@example.test", "Admin One", null, UserStatus.ACTIVE);
    admin.addRole(UserRole.ADMIN);
    adminId = users.saveAndFlush(admin).getId();

    Drone drone =
        drones.saveAndFlush(
            new Drone(
                inspectorId,
                "DJI-M3E-" + UUID.randomUUID(),
                "DJI Mavic 3E",
                "DJI",
                "{\"mtowKg\":1.0}",
                DroneServiceability.ACTIVE,
                null,
                "REG-M3E",
                "drones/reg.pdf",
                Instant.now().plus(365, ChronoUnit.DAYS)));

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory("cat-" + UUID.randomUUID(), "Bridge", "Bridge category", true));

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                customerId,
                category.getId(),
                "BR-" + UUID.randomUUID(),
                "Dong Nai Bridge",
                "Steel bridge",
                "{\"label\":\"Dong Nai\"}",
                null,
                null,
                null,
                List.of("Span 1"),
                customerId));

    Inspection inspection =
        new Inspection(
            customerId,
            asset.getId(),
            inspectorId,
            drone.getId(),
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS));
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.COMPLETED);
    completedInspectionId = inspections.saveAndFlush(inspection).getId();
  }

  @Test
  void customerAndManagerRateCompletedInspectionEditWithinWindowAndSeeAggregates()
      throws Exception {
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/inspector-rating", completedInspectionId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(customerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "overallScore": 4,
                      "criterionScores": {
                        "evidenceQuality": 4,
                        "communication": 5,
                        "professionalism": 4
                      },
                      "comment": "Clear thermal photos."
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myRating.overallScore").value(4))
        .andExpect(jsonPath("$.data.customerRating.overallScore").value(4))
        .andExpect(jsonPath("$.data.managerRating").doesNotExist())
        .andExpect(jsonPath("$.data.inspectorAverageRating").value(4.0))
        .andExpect(jsonPath("$.data.inspectorRatingCount").value(1));

    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/inspector-rating", completedInspectionId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(managerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "overallScore": 5,
                      "criterionScores": {
                        "sopAndSafety": 5,
                        "technicalAccuracy": 5,
                        "timeliness": 5
                      },
                      "comment": "Zero SOP deviations."
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myRating.overallScore").value(5))
        .andExpect(jsonPath("$.data.customerRating.overallScore").value(4))
        .andExpect(jsonPath("$.data.managerRating.overallScore").value(5))
        .andExpect(jsonPath("$.data.inspectorAverageRating").value(4.5))
        .andExpect(jsonPath("$.data.inspectorRatingCount").value(2));

    // Inspection detail includes average rating and count
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}", completedInspectionId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(customerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorAverageRating").value(4.5))
        .andExpect(jsonPath("$.data.inspectorRatingCount").value(2));

    // Inspector self dashboard endpoint sees both ratings & averages
    mockMvc
        .perform(
            get("/api/v1/inspectors/me/ratings")
                .with(
                    jwt()
                        .jwt(j -> j.subject(inspectorId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.historyVisible").value(true))
        .andExpect(jsonPath("$.data.summary.averageRating").value(4.5))
        .andExpect(jsonPath("$.data.summary.customerAverageRating").value(4.0))
        .andExpect(jsonPath("$.data.summary.managerAverageRating").value(5.0))
        .andExpect(jsonPath("$.data.ratings.length()").value(2));

    // Customer profile view only receives aggregate summary (no raw comments)
    mockMvc
        .perform(
            get("/api/v1/inspectors/{inspectorId}/ratings", inspectorId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(customerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.historyVisible").value(false))
        .andExpect(jsonPath("$.data.ratings.length()").value(0))
        .andExpect(jsonPath("$.data.summary.averageRating").value(4.5));

    // Admin has read-only access and is denied write
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/inspector-rating", completedInspectionId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(adminId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "overallScore": 3,
                      "criterionScores": {
                        "sopAndSafety": 3,
                        "technicalAccuracy": 3,
                        "timeliness": 3
                      }
                    }
                    """))
        .andExpect(status().isForbidden());

    // Simulate 8 days elapsed -> 409 RATING_EDIT_WINDOW_EXPIRED
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE inspector_ratings SET created_at = NOW() - INTERVAL '8 days' "
            + "WHERE inspection_id = ? AND rater_role = 'CUSTOMER'",
        completedInspectionId);
    entityManager.clear();

    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/inspector-rating", completedInspectionId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(customerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "overallScore": 5,
                      "criterionScores": {
                        "evidenceQuality": 5,
                        "communication": 5,
                        "professionalism": 5
                      }
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RATING_EDIT_WINDOW_EXPIRED"));
  }
}
