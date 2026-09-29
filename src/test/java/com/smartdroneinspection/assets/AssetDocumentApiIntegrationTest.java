package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetDocumentApiIntegrationTest {

  private static final byte[] PNG =
      java.util.Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/o2cAAAAASUVORK5CYII=");
  private static final byte[] NOT_AN_IMAGE = new byte[] {1, 2, 3};

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean EvidenceObjectStore objectStore;

  private final Map<String, byte[]> storedObjects = new ConcurrentHashMap<>();
  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws IOException {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    storedObjects.clear();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              storedObjects.put(
                  invocation.getArgument(0),
                  invocation.getArgument(3, InputStream.class).readAllBytes());
              return null;
            })
        .when(objectStore)
        .put(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(InputStream.class));
    org.mockito.Mockito.when(objectStore.open(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> new ByteArrayInputStream(storedObjects.get(invocation.getArgument(0))));
    fixture = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void activeAssetDocumentUploadsAndListsForOwnOrgOnly() throws Exception {
    UUID assetId = seedActiveAsset();

    mockMvc
        .perform(
            multipart("/api/v1/assets/{id}/documents", assetId)
                .file(new MockMultipartFile("file", "permit.png", "image/png", PNG))
                .param("documentType", "PERMIT")
                .param("documentDate", "2026-09-01")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fileName").value("permit.png"))
        .andExpect(jsonPath("$.data.contentType").value("image/png"));

    assertThat(storedObjects).hasSize(1);

    mockMvc
        .perform(get("/api/v1/assets/{id}/documents", assetId).with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));

    mockMvc
        .perform(
            get("/api/v1/assets/{id}/documents", assetId).with(client(fixture.otherClientId())))
        .andExpect(status().isNotFound());
  }

  @Test
  void unsupportedTypeOversizeAndPendingAssetsAreRejected() throws Exception {
    UUID assetId = seedActiveAsset();
    mockMvc
        .perform(
            multipart("/api/v1/assets/{id}/documents", assetId)
                .file(
                    new MockMultipartFile(
                        "file", "odd.bin", "application/octet-stream", NOT_AN_IMAGE))
                .param("documentType", "OTHER")
                .with(client(fixture.clientId())))
        .andExpect(status().isUnsupportedMediaType());

    byte[] big = new byte[11 * 1024 * 1024];
    mockMvc
        .perform(
            multipart("/api/v1/assets/{id}/documents", assetId)
                .file(new MockMultipartFile("file", "big.png", "image/png", big))
                .param("documentType", "PERMIT")
                .with(client(fixture.clientId())))
        .andExpect(status().isPayloadTooLarge());

    UUID pendingId = seedPendingAsset();
    mockMvc
        .perform(
            multipart("/api/v1/assets/{id}/documents", pendingId)
                .file(new MockMultipartFile("file", "ok.png", "image/png", PNG))
                .param("documentType", "PERMIT")
                .with(client(fixture.clientId())))
        .andExpect(status().isConflict());

    assertThat(storedObjects).isEmpty();
  }

  private UUID seedActiveAsset() {
    return assets
        .saveAndFlush(
            new Asset(
                fixture.organizationId(),
                fixture.categoryId(),
                "ACT-" + UUID.randomUUID(),
                "Active asset",
                null,
                "District 1",
                null,
                null,
                null,
                fixture.clientId()))
        .getId();
  }

  private UUID seedPendingAsset() {
    return assets
        .saveAndFlush(
            Asset.clientCreate(
                fixture.organizationId(),
                fixture.categoryId(),
                "PA-" + UUID.randomUUID(),
                "Pending asset",
                null,
                "District 1",
                null,
                null,
                null,
                fixture.clientId()))
        .getId();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENT"));
  }
}
