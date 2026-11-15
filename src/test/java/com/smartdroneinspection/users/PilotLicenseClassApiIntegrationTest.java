package com.smartdroneinspection.users;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.users.domain.PilotLicenseClass;
import com.smartdroneinspection.users.repository.PilotLicenseClassRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PilotLicenseClassApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired PilotLicenseClassRepository classes;

  private MockMvc mockMvc;
  private UUID adminId;
  private UUID managerId;
  private UUID inspectorId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    adminId = UUID.randomUUID();
    managerId = UUID.randomUUID();
    inspectorId = UUID.randomUUID();
  }

  @Test
  void onlyAdminCanCreateUpdateOrDeactivateLicenseClasses() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pilot-license-classes")
                .with(
                    jwt()
                        .jwt(token -> token.subject(adminId.toString()))
                        .authorities(() -> "ROLE_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Class A\",\"description\":\"VLOS\",\"active\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("Class A"));

    mockMvc
        .perform(
            post("/api/v1/pilot-license-classes")
                .with(
                    jwt()
                        .jwt(token -> token.subject(managerId.toString()))
                        .authorities(() -> "ROLE_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Class B\",\"active\":true}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/pilot-license-classes")
                .with(
                    jwt()
                        .jwt(token -> token.subject(inspectorId.toString()))
                        .authorities(() -> "ROLE_INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Class C\",\"active\":true}"))
        .andExpect(status().isForbidden());

    PilotLicenseClass licenseClass =
        classes.findAllByOrderByNameAsc().stream()
            .filter(item -> item.getName().equals("Class A"))
            .findFirst()
            .orElseThrow();
    mockMvc
        .perform(
            put("/api/v1/pilot-license-classes/{id}", licenseClass.getId())
                .with(
                    jwt()
                        .jwt(token -> token.subject(adminId.toString()))
                        .authorities(() -> "ROLE_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Class A\",\"description\":\"VLOS updated\",\"active\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("VLOS updated"));

    mockMvc
        .perform(
            delete("/api/v1/pilot-license-classes/{id}", licenseClass.getId())
                .with(
                    jwt()
                        .jwt(token -> token.subject(adminId.toString()))
                        .authorities(() -> "ROLE_ADMIN")))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(
            classes.findById(licenseClass.getId()).orElseThrow().isActive())
        .isFalse();
  }

  @Test
  void activeListOmitsDeactivatedClassesButAdminListRetainsThem() throws Exception {
    PilotLicenseClass active = classes.saveAndFlush(new PilotLicenseClass("Class active", null));
    PilotLicenseClass inactive = new PilotLicenseClass("Class inactive", null);
    inactive.setActive(false);
    classes.saveAndFlush(inactive);

    mockMvc
        .perform(
            get("/api/v1/pilot-license-classes")
                .with(
                    jwt()
                        .jwt(token -> token.subject(inspectorId.toString()))
                        .authorities(() -> "ROLE_INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '" + active.getId() + "')]").exists())
        .andExpect(jsonPath("$.data[?(@.id == '" + inactive.getId() + "')]").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/pilot-license-classes/admin")
                .with(
                    jwt()
                        .jwt(token -> token.subject(adminId.toString()))
                        .authorities(() -> "ROLE_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '" + inactive.getId() + "')]").exists());
  }
}
