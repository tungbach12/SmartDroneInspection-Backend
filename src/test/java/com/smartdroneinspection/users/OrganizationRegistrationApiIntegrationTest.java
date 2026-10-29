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
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.repository.OrganizationRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrganizationRegistrationApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
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
  void registrationCreatesActiveOrganizationCustomerAndFirstAssetAtomically() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "customer-" + unique + "@example.test";
    String code = "ORG-" + unique.substring(0, 16);
    var category =
        categories.saveAndFlush(
            new AssetCategory("REG-" + unique.substring(0, 12), "Bridge", null, true));
    String assetCode = "AST-" + unique.substring(0, 16);
    String body = registrationBody(email, code, assetCode, category.getId(), 10.9, 106.8);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.registrationStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.user.roles", hasItem("CUSTOMER")))
        .andExpect(jsonPath("$.data.user.actorZone").value("CUSTOMER_ORGANIZATION"));

    User customer = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(customer.roleValues()).containsExactly("CUSTOMER");
    assertThat(assets.existsByOrganizationIdAndCode(customer.getOrganizationId(), assetCode))
        .isTrue();
    Organization organization = organizations.findById(customer.getOrganizationId()).orElseThrow();
    assertThat(organization.getStatus()).isEqualTo("ACTIVE");
    assertThat(organization.getCreatedByUserId()).isEqualTo(customer.getId());
  }

  @Test
  void unlistedAirspaceCreatesDisabledProspectAndNoOrganization() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "pending-" + unique + "@example.test";
    String code = "PEND-" + unique.substring(0, 16);
    var category =
        categories.saveAndFlush(
            new AssetCategory("PEN-" + unique.substring(0, 12), "Bridge", null, true));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registrationBody(
                        email,
                        code,
                        "AST-" + unique.substring(0, 16),
                        category.getId(),
                        10.9,
                        106.7)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.registrationStatus").value("PENDING_AIRSPACE_REVIEW"))
        .andExpect(jsonPath("$.data.user.actorZone").value("PROSPECT_CUSTOMER"));

    User prospect = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(prospect.getOrganizationId()).isNull();
    assertThat(prospect.getStatus().name()).isEqualTo("DISABLED");
    assertThat(organizations.findByRegistrationCodeIgnoreCase(code)).isEmpty();
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
                        "BLOCK-" + unique.substring(0, 16),
                        "BLK-" + unique.substring(0, 16),
                        category.getId(),
                        10.8188,
                        106.6519)))
        .andExpect(status().isForbidden());

    assertThat(users.findByNormalizedEmail(User.normalizeEmail(email))).isEmpty();
  }

  private String registrationBody(
      String email,
      String organizationCode,
      String assetCode,
      UUID categoryId,
      double lat,
      double lon) {
    return """
        {
          "email":"%s",
          "fullName":"Customer Admin",
          "organizationName":"Infrastructure Company",
          "organizationCode":"%s",
          "password":"a sufficiently long passphrase",
          "firstAsset":{"code":"%s","name":"First bridge","categoryId":"%s",
            "locationText":"District 1","latitude":%s,"longitude":%s}
        }
        """
        .formatted(email, organizationCode, assetCode, categoryId, lat, lon);
  }
}
