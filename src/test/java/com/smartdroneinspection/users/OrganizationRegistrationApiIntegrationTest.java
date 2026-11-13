package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.repository.ProspectiveCustomerRegistrationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
      "smartdroneinspection.auth.otp.enabled=false",
      "smartdroneinspection.auth.otp.challenge-pepper="
    })
@Import(TestcontainersConfiguration.class)
class OrganizationRegistrationApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired ProspectiveCustomerRegistrationRepository prospectiveRegistrations;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  @Test
  void registrationCreatesActiveCustomerAndFirstAssetAtomically() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "customer-" + unique + "@example.test";
    var category =
        categories.saveAndFlush(
            new AssetCategory("REG-" + unique.substring(0, 12), "Bridge", null, true));
    String assetCode = "AST-" + unique.substring(0, 16);
    String body = registrationBody(email, assetCode, category.getId(), 10.5500, 106.8500);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.registrationStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.user.roles", hasItem("CUSTOMER")))
        .andExpect(jsonPath("$.data.user.actorZone").value("CUSTOMER"));

    User customer = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(customer.roleValues()).containsExactly("CUSTOMER");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM assets WHERE owner_customer_id = ? AND asset_code = ?",
                Integer.class,
                customer.getId(),
                assetCode.toUpperCase(java.util.Locale.ROOT)))
        .isEqualTo(1);
  }

  @Test
  void registrationWithoutFirstAssetCreatesActiveCustomer() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "direct-" + unique + "@example.test";
    String body =
        """
        {
          "email":"%s",
          "fullName":"Direct Customer Admin",
          "organizationName":"Direct Infrastructure Company",
          "organizationCode":"DIR-%s",
          "password":"a sufficiently long passphrase"
        }
        """
            .formatted(email, unique.substring(0, 16));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.registrationStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.user.roles", hasItem("CUSTOMER")))
        .andExpect(jsonPath("$.data.user.actorZone").value("CUSTOMER"));

    User customer = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(customer.roleValues()).containsExactly("CUSTOMER");
  }

  @Test
  void unlistedAirspaceCreatesDisabledProspectAndNoOrganization() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "pending-" + unique + "@example.test";
    String assetCode = "AST-" + unique.substring(0, 16);
    String code = "ORG-" + assetCode;
    var category =
        categories.saveAndFlush(
            new AssetCategory("PEN-" + unique.substring(0, 12), "Bridge", null, true));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody(email, assetCode, category.getId(), 10.9, 106.7)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.registrationStatus").value("PENDING_AIRSPACE_REVIEW"))
        .andExpect(jsonPath("$.data.user.actorZone").value("CUSTOMER"));

    User prospect = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(prospect.getStatus().name()).isEqualTo("DISABLED");
    assertThat(
            prospectiveRegistrations.existsByOrganizationCodeIgnoreCaseAndStatus(code, "PENDING"))
        .isTrue();
  }

  @Test
  void noFlyAirspaceRejectsRegistrationBeforeCreatingAnAccount() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "blocked-" + unique + "@example.test";
    var category =
        categories.saveAndFlush(
            new AssetCategory("BLK-" + unique.substring(0, 12), "Bridge", null, true));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registrationBody(
                        email,
                        "BLK-" + unique.substring(0, 16),
                        category.getId(),
                        10.8188,
                        106.6519)))
        .andExpect(status().isForbidden());

    assertThat(users.findByNormalizedEmail(User.normalizeEmail(email))).isEmpty();
  }

  private String registrationBody(
      String email, String assetCode, UUID categoryId, double lat, double lon) {
    String organizationCode = "ORG-" + assetCode;
    return """
        {
          "email":"%s",
          "fullName":"Customer Admin",
          "organizationName":"Infrastructure Company",
          "organizationCode":"%s",
          "password":"a sufficiently long passphrase",
          "firstAsset":{"code":"%s","name":"First bridge","categoryId":"%s",
            "locationText":"District 1","latitude":%s,"longitude":%s,
            "ownershipInformation":"{\\"ownership\\":\\"Customer-owned bridge\\"}"}
        }
        """
        .formatted(email, organizationCode, assetCode, categoryId, lat, lon);
  }
}
