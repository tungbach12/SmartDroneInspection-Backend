package com.smartdroneinspection.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.infrastructure.payment.VnPayCrypto;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "app.payment.vnpay.enabled=true",
      "app.payment.vnpay.tmn-code=DEMOTMN1",
      "app.payment.vnpay.hash-secret=TEST_VNPAY_SECRET_KEY_1234567890",
      "app.payment.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
      "app.payment.vnpay.return-url=http://localhost:8080/api/v1/webhooks/vnpay/return",
      "app.payment.vnpay.frontend-return-url=http://localhost:3000/client/reports/{inspectionId}"
    })
@Import(TestcontainersConfiguration.class)
class VnPayPaymentIntegrationTest {

  private static final String TEST_HASH_SECRET = "TEST_VNPAY_SECRET_KEY_1234567890";
  private static final byte[] VALID_PNG_BYTES =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository users;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired ServiceRequestOccurrenceRepository occurrences;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  private UUID customerUserId;
  private UUID otherCustomerUserId;
  private UUID managerUserId;
  private UUID adminUserId;
  private UUID inspectorUserId;
  private UUID droneId;
  private UUID assetId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    jdbcTemplate.execute("DELETE FROM inspection_finance_settings");
    jdbcTemplate.execute("DELETE FROM service_request_occurrences");
    jdbcTemplate.execute("DELETE FROM service_requests");
    jdbcTemplate.execute("DELETE FROM service_request_quotes");

    managerUserId =
        createPlatformUser(
            UserRole.MANAGER,
            "manager." + UUID.randomUUID() + "@platform.internal",
            "Manager User");

    adminUserId =
        createPlatformUser(
            UserRole.ADMIN, "admin." + UUID.randomUUID() + "@platform.internal", "Admin User");

    try {
      mockMvc
          .perform(
              put("/api/v1/contracts/admin/finance-settings")
                  .with(principal(adminUserId, "ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("commissionRate", 15.00, "vatRate", 10.00, "tncnRate", 10.00))))
          .andExpect(status().isOk());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    customerUserId =
        createUser(
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER,
            "customer." + UUID.randomUUID() + "@vnpay.test",
            "Customer Primary");

    otherCustomerUserId =
        createUser(
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER,
            "other." + UUID.randomUUID() + "@other.test",
            "Customer Other Org");

    inspectorUserId =
        createUser(
            UserRole.INSPECTOR,
            ActorZone.INSPECTOR,
            "inspector." + UUID.randomUUID() + "@partner.test",
            "Partner Pilot");

    jdbcTemplate.update(
        "INSERT INTO inspector_profiles (user_id, pilot_license_number, pilot_license_class, pilot_license_expires_at, pilot_license_file_key) VALUES (?, ?, ?, ?, ?)",
        inspectorUserId,
        "VN-UAV-PILOT-2026",
        "CLASS_B",
        java.sql.Timestamp.from(Instant.now().plus(365, ChronoUnit.DAYS)),
        "licenses/pilot-cert.pdf");

    try {
      mockMvc
          .perform(
              post("/api/v1/contracts/partners/inspectors")
                  .with(principal(managerUserId, "MANAGER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(Map.of("inspectorUserId", inspectorUserId))))
          .andExpect(status().isCreated());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    Drone drone =
        drones.saveAndFlush(
            Drone.createInspectorOwned(
                inspectorUserId,
                "DJI-M350-" + UUID.randomUUID().toString().substring(0, 8),
                "Matrice 350 RTK",
                "DJI",
                "REG-VN-350",
                "drones/registration.pdf",
                Instant.now().plus(365, ChronoUnit.DAYS)));
    droneId = drone.getId();

    try {
      mockMvc
          .perform(
              post("/api/v1/contracts/partners/drones")
                  .with(principal(managerUserId, "MANAGER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("droneId", droneId))))
          .andExpect(status().isCreated());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "cat-" + UUID.randomUUID().toString().substring(0, 8),
                "Telecom Towers",
                "Telecommunication cellular towers",
                true));

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                customerUserId,
                category.getId(),
                "TOWER-" + UUID.randomUUID().toString().substring(0, 8),
                "Tower Alpha",
                "Primary broadcasting tower",
                "{\"latitude\":10.7769,\"longitude\":106.7009,\"city\":\"Ho Chi Minh City\"}",
                null,
                null,
                null,
                List.of("Section 1", "Section 2"),
                customerUserId));
    assetId = asset.getId();
  }

  @Test
  @DisplayName(
      "Full VNPay Lifecycle: Create Signed URL, IPN & Return Settlement, Idempotency, and PDF Unlocking")
  void testFullVnPayLifecycle() throws Exception {
    PreparedInspection prepared = prepareInspectionReadyForPayment();
    UUID requestId = prepared.requestId();
    UUID occurrenceId = prepared.occurrenceId();
    UUID inspectionId = prepared.inspectionId();
    UUID reportVersionId = prepared.reportVersionId();
    long agreedPrice = 5000000L;

    // 1. Deliverable PDF export is available immediately upon report publication
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    // 2. Unauthorized org cannot create VNPay URL
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/vnpay/create-payment-url",
                    requestId,
                    occurrenceId)
                .with(principal(otherCustomerUserId, "CUSTOMER")))
        .andExpect(status().isNotFound());

    // 3. Customer creates signed VNPay checkout URL via inspection endpoint
    String urlResponse =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/vnpay/create-payment-url", inspectionId)
                    .with(principal(customerUserId, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderCode").isNumber())
            .andExpect(jsonPath("$.data.amount").value(agreedPrice))
            .andExpect(
                jsonPath("$.data.checkoutUrl")
                    .value(
                        Matchers.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")))
            .andExpect(
                jsonPath("$.data.checkoutUrl").value(Matchers.containsString("vnp_SecureHash=")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    long allocatedOrderCode = objectMapper.readTree(urlResponse).at("/data/orderCode").longValue();

    ServiceRequestOccurrence occAfterUrl = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(occAfterUrl.getPayosOrderCode()).isEqualTo(allocatedOrderCode);
    assertThat(occAfterUrl.getPaymentMethod()).isEqualTo("VNPAY");
    assertThat(occAfterUrl.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 4. IPN rejects invalid checksum with RspCode 97
    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/ipn")
                .param("vnp_TxnRef", String.valueOf(allocatedOrderCode))
                .param("vnp_Amount", String.valueOf(agreedPrice * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "14226112")
                .param("vnp_SecureHash", "invalid_forged_hash"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("97"));

    assertThat(occurrences.findById(occurrenceId).orElseThrow().getPaymentStatus())
        .isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 5. IPN rejects amount mismatch with RspCode 04
    Map<String, String> mismatchParams = new LinkedHashMap<>();
    mismatchParams.put("vnp_TxnRef", String.valueOf(allocatedOrderCode));
    mismatchParams.put("vnp_Amount", String.valueOf(1000000L * 100L));
    mismatchParams.put("vnp_ResponseCode", "00");
    mismatchParams.put("vnp_TransactionStatus", "00");
    mismatchParams.put("vnp_TransactionNo", "14226112");
    String mismatchQuery = VnPayCrypto.buildSignedQueryString(mismatchParams, TEST_HASH_SECRET);
    String mismatchHash = mismatchQuery.substring(mismatchQuery.indexOf("&vnp_SecureHash=") + 16);

    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/ipn")
                .param("vnp_TxnRef", String.valueOf(allocatedOrderCode))
                .param("vnp_Amount", String.valueOf(1000000L * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "14226112")
                .param("vnp_SecureHash", mismatchHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("04"));

    // 6. Browser Return URL with valid HMAC-SHA512 settles payment and redirects 302 to Frontend
    Map<String, String> validParams = new LinkedHashMap<>();
    validParams.put("vnp_TxnRef", String.valueOf(allocatedOrderCode));
    validParams.put("vnp_Amount", String.valueOf(agreedPrice * 100L));
    validParams.put("vnp_ResponseCode", "00");
    validParams.put("vnp_TransactionStatus", "00");
    validParams.put("vnp_TransactionNo", "14226112");
    validParams.put("vnp_BankCode", "NCB");
    String validQuery = VnPayCrypto.buildSignedQueryString(validParams, TEST_HASH_SECRET);
    String validHash = validQuery.substring(validQuery.indexOf("&vnp_SecureHash=") + 16);

    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/return")
                .param("vnp_TxnRef", String.valueOf(allocatedOrderCode))
                .param("vnp_Amount", String.valueOf(agreedPrice * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "14226112")
                .param("vnp_BankCode", "NCB")
                .param("vnp_SecureHash", validHash))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location",
                    "http://localhost:3000/client/reports/"
                        + inspectionId
                        + "?payment=success&gateway=vnpay&orderCode="
                        + allocatedOrderCode));

    ServiceRequestOccurrence settledOcc = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(settledOcc.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAID);
    assertThat(settledOcc.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.ELIGIBLE);
    assertThat(settledOcc.getPayoutReference()).isNull();
    assertThat(settledOcc.getPaymentMethod()).isEqualTo("VNPAY");
    assertThat(settledOcc.getPaymentReference()).isEqualTo("VNPAY-14226112");

    // 7. Subsequent IPN delivery returns RspCode 02 (Order already confirmed)
    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/ipn")
                .param("vnp_TxnRef", String.valueOf(allocatedOrderCode))
                .param("vnp_Amount", String.valueOf(agreedPrice * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "14226112")
                .param("vnp_BankCode", "NCB")
                .param("vnp_SecureHash", validHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("02"));

    // 8. Deliverable download is now unblocked
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());
  }

  private record PreparedInspection(
      UUID requestId, UUID occurrenceId, UUID inspectionId, UUID reportVersionId) {}

  private PreparedInspection prepareInspectionReadyForPayment() throws Exception {
    ZoneId utc = ZoneId.of("UTC");
    LocalDate reqDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    Instant start = reqDate.atTime(9, 0).atZone(utc).toInstant();
    Instant end = start.plus(2, ChronoUnit.HOURS);

    String reqResp =
        mockMvc
            .perform(
                post("/api/v1/contracts/requests")
                    .with(principal(customerUserId, "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "assetId",
                                assetId,
                                "description",
                                "VNPay full lifecycle test",
                                "requestedFrom",
                                reqDate,
                                "requestedUntil",
                                reqDate,
                                "timezone",
                                "UTC",
                                "requestType",
                                "ONE_TIME"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID requestId = UUID.fromString(objectMapper.readTree(reqResp).at("/data/id").stringValue());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/quotes", requestId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "scopeSnapshot", "Tower inspection quote",
                            "firstOccurrenceStartAt", start,
                            "firstOccurrenceEndAt", end,
                            "inspectorId", inspectorUserId,
                            "droneId", droneId,
                            "pricePerOccurrenceVnd", 5000000.00))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/accept", requestId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    ServiceRequestOccurrence occurrence =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst();
    UUID occurrenceId = occurrence.getId();

    byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
    mockMvc
        .perform(
            multipart(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/signed-contract",
                    requestId,
                    occurrenceId)
                .file(
                    new MockMultipartFile(
                        "file",
                        "occurrence-contract.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docxBytes))
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

    String assignResp =
        mockMvc
            .perform(
                post(
                        "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/assign",
                        requestId,
                        occurrenceId)
                    .with(principal(managerUserId, "MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "plannedStartAt", start,
                                "plannedEndAt", end,
                                "inspectorId", inspectorUserId,
                                "droneId", droneId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID inspectionId =
        UUID.fromString(
            objectMapper.readTree(assignResp).at("/data/occurrences/0/inspectionId").stringValue());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("fileKey", "permits/p1.pdf", "fileName", "p1.pdf"))))
        .andExpect(status().isOk());

    String sessionResp =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                    .with(principal(inspectorUserId, "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("preFlightChecklistNote", "All preflight checks pass"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID sessionId =
        UUID.fromString(objectMapper.readTree(sessionResp).at("/data/id").stringValue());

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "evidence1.png", "image/png", VALID_PNG_BYTES))
                .param("fieldSessionId", sessionId.toString())
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/completion",
                    inspectionId,
                    sessionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("outcomeNotes", "Completed session"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("noFindingsAcknowledged", true))))
        .andExpect(status().isOk());

    String draftResp =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", inspectionId)
                    .with(principal(inspectorUserId, "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID reportVersionId =
        UUID.fromString(objectMapper.readTree(draftResp).at("/data/id").stringValue());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/verify",
                    inspectionId,
                    reportVersionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/submit",
                    inspectionId,
                    reportVersionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/review",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER"))
                .param("approve", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/accept-report",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER"))
                .param("reportVersionId", reportVersionId.toString()))
        .andExpect(status().isOk());

    return new PreparedInspection(requestId, occurrenceId, inspectionId, reportVersionId);
  }

  private UUID createPlatformUser(UserRole role, String email, String fullName) {
    User user = new User(email, fullName, null, UserStatus.ACTIVE, ActorZone.PLATFORM);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createUser(UserRole role, ActorZone zone, String email, String fullName) {
    User user = new User(email, fullName, null, UserStatus.ACTIVE, zone);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private static RequestPostProcessor principal(UUID id, String role) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
