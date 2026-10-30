package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.service.DroneEvidenceUploadService;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DroneEvidenceUploadServiceTest {

  private static final byte[] PDF =
      "%PDF-1.7\nDocument".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  @Test
  void storesInspectorEvidenceInOwnerAndTypeScopedGeneratedKey() throws Exception {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    UserAccess users = org.mockito.Mockito.mock(UserAccess.class);
    UUID inspectorId = UUID.randomUUID();
    when(users.findActiveUser(inspectorId)).thenReturn(Optional.of(activeInspector(inspectorId)));
    var service = new DroneEvidenceUploadService(Optional.of(store), users);

    var response = service.upload(inspectorId, "OWNERSHIP", file(PDF));

    assertThat(response.fileKey())
        .matches(
            "drone-registration-applications/"
                + inspectorId
                + "/evidence/OWNERSHIP/[0-9a-f-]{36}\\.pdf");
    verify(store)
        .put(
            anyString(),
            org.mockito.ArgumentMatchers.eq("application/pdf"),
            org.mockito.ArgumentMatchers.eq((long) PDF.length),
            any(InputStream.class));
  }

  @Test
  void requiresAnActiveInspectorPartnerToUpload() {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    UserAccess users = org.mockito.Mockito.mock(UserAccess.class);
    UUID userId = UUID.randomUUID();
    when(users.findActiveUser(userId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    userId, Set.of("CUSTOMER"), null, "CUSTOMER_ORGANIZATION")));
    var service = new DroneEvidenceUploadService(Optional.of(store), users);

    assertThatThrownBy(() -> service.upload(userId, "OWNERSHIP", file(PDF)))
        .isInstanceOf(AuthException.class);
    verifyNoInteractions(store);
  }

  @Test
  void rejectsUnknownTypeBeforeStorage() {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    var service =
        new DroneEvidenceUploadService(
            Optional.of(store), org.mockito.Mockito.mock(UserAccess.class));

    assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "OTHER", file(PDF)))
        .isInstanceOf(AuthException.class);
    verifyNoInteractions(store);
  }

  @Test
  void rejectsSpoofedContentBeforeStorage() {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    UserAccess users = org.mockito.Mockito.mock(UserAccess.class);
    UUID inspectorId = UUID.randomUUID();
    when(users.findActiveUser(inspectorId)).thenReturn(Optional.of(activeInspector(inspectorId)));
    var service = new DroneEvidenceUploadService(Optional.of(store), users);

    assertThatThrownBy(
            () ->
                service.upload(
                    inspectorId,
                    "REGISTRATION",
                    new MockMultipartFile(
                        "file", "fake.pdf", "application/pdf", new byte[] {1, 2, 3})))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(store);
  }

  @Test
  void adminCanReadEvidenceButCustomerCannot() throws Exception {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    UserAccess users = org.mockito.Mockito.mock(UserAccess.class);
    UUID adminId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    when(users.findActiveUser(adminId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    adminId,
                    Set.of("ADMIN"),
                    null,
                    "PLATFORM",
                    "Admin",
                    "admin@example.test",
                    null,
                    null,
                    null,
                    null)));
    when(users.findActiveUser(customerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    customerId,
                    Set.of("CUSTOMER"),
                    UUID.randomUUID(),
                    "CUSTOMER_ORGANIZATION",
                    "Customer",
                    "customer@example.test",
                    null,
                    null,
                    null,
                    null)));
    UUID inspectorId = UUID.randomUUID();
    String key =
        "drone-registration-applications/"
            + inspectorId
            + "/evidence/REGISTRATION/00000000-0000-0000-0000-000000000001.pdf";
    when(store.open(key)).thenReturn(new ByteArrayInputStream(PDF));
    var service = new DroneEvidenceUploadService(Optional.of(store), users);

    var content = service.open(adminId, key);

    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.stream().readAllBytes()).isEqualTo(PDF);
    assertThatThrownBy(() -> service.open(customerId, key)).isInstanceOf(AuthException.class);
  }

  private static UserAccess.ActiveUser activeInspector(UUID inspectorId) {
    return new UserAccess.ActiveUser(
        inspectorId,
        Set.of("INSPECTOR"),
        null,
        "INSPECTOR_PARTNER",
        "Inspector",
        "inspector@example.test",
        "PILOT-1",
        "A1",
        java.time.Instant.now().plusSeconds(86400),
        "license-key");
  }

  private static MockMultipartFile file(byte[] content) {
    return new MockMultipartFile("file", "proof.pdf", "application/pdf", content);
  }
}
