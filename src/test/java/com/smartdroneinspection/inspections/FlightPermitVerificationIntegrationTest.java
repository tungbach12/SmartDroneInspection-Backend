package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FlightPermitVerificationIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionRepository inspections;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private UUID customerId;
  private UUID managerId;
  private UUID inspectorId;
  private UUID assetId;
  private UUID droneId;
  private UUID inspectionId;
  private Instant plannedStart;
  private Instant plannedEnd;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    User customer =
        users.saveAndFlush(
            new User(
                "cust-" + UUID.randomUUID() + "@test.local", "Customer", null, UserStatus.ACTIVE));
    customer.addRole(UserRole.CUSTOMER);
    customerId = users.saveAndFlush(customer).getId();

    User manager =
        users.saveAndFlush(
            new User(
                "mgr-" + UUID.randomUUID() + "@test.local", "Manager", null, UserStatus.ACTIVE));
    manager.addRole(UserRole.MANAGER);
    managerId = users.saveAndFlush(manager).getId();

    User inspector =
        users.saveAndFlush(
            new User(
                "insp-" + UUID.randomUUID() + "@test.local", "Inspector", null, UserStatus.ACTIVE));
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();
    jdbcTemplate.update(
        "INSERT INTO inspector_profiles (user_id, pilot_license_number, pilot_license_class, pilot_license_expires_at, pilot_license_file_key) VALUES (?, ?, ?, ?, ?)",
        inspectorId,
        "LIC-PERMIT-TEST",
        "CLASS_B",
        java.sql.Timestamp.from(Instant.now().plus(365, ChronoUnit.DAYS)),
        "licenses/permit-test.pdf");

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "CAT-" + UUID.randomUUID().toString().substring(0, 6), "Bridge", "Bridge", true));
    Asset asset =
        assets.saveAndFlush(
            new Asset(
                customerId,
                category.getId(),
                "BR-" + UUID.randomUUID().toString().substring(0, 6),
                "Cầu Thủ Thiêm",
                "Bridge structure",
                "10.7789, 106.7082",
                new java.math.BigDecimal("10.7789"),
                new java.math.BigDecimal("106.7082"),
                null,
                List.of("Deck"),
                customerId));
    asset.markCustomerOwned(customerId);
    assets.saveAndFlush(asset);
    assetId = asset.getId();

    Drone drone =
        new Drone(
            inspectorId,
            "SN-DJI-M300-88",
            "DJI Matrice 300",
            "DJI",
            "{\"mtowKg\":6.3}",
            DroneServiceability.ACTIVE,
            null,
            "REG-88",
            "drones/reg-88.pdf",
            Instant.now().plus(365, ChronoUnit.DAYS));
    droneId = drones.saveAndFlush(drone).getId();

    plannedStart = Instant.now().minus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    plannedEnd = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    Inspection inspection =
        new Inspection(customerId, assetId, inspectorId, droneId, plannedStart, plannedEnd);
    inspection.markPartnerAssignment();
    inspectionId = inspections.saveAndFlush(inspection).getId();
  }

  @Test
  void enforcesFileMetadataCrossChecksAndMandatoryManagerApprovalBeforeFlight() throws Exception {
    String validFrom = plannedStart.minus(1, ChronoUnit.HOURS).toString();
    String validTo = plannedEnd.plus(2, ChronoUnit.HOURS).toString();

    // 1. Reject fake PDF (invalid magic bytes)
    MockMultipartFile fakePdf =
        new MockMultipartFile(
            "file",
            "fake.pdf",
            "application/pdf",
            "not-really-a-pdf".getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .file(fakePdf)
                .param("permitNumber", "GP-2026-100")
                .param("issuingAuthority", "Cục Tác chiến - BTTM")
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERMIT_FILE"));

    // 2. Reject mismatched drone serial number
    MockMultipartFile validPdf =
        new MockMultipartFile(
            "file",
            "permit.pdf",
            "application/pdf",
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .file(validPdf)
                .param("permitNumber", "GP-2026-100")
                .param("issuingAuthority", "Cục Tác chiến - BTTM")
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .param("droneSerial", "WRONG-SERIAL-99")
                .with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERMIT_DRONE_MISMATCH"));

    // 3. Reject permit validity window that does not cover plannedEnd
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .file(validPdf)
                .param("permitNumber", "GP-2026-100")
                .param("issuingAuthority", "Cục Tác chiến - BTTM")
                .param("validFrom", validFrom)
                .param("validTo", plannedEnd.minus(30, ChronoUnit.MINUTES).toString())
                .with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERMIT_SCHEDULE_MISMATCH"));

    // 4. Valid upload -> PENDING_REVIEW, pendingAction = PERMIT_REVIEW
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .file(validPdf)
                .param("permitNumber", "GP-2026-100")
                .param("issuingAuthority", "Cục Tác chiến - BTTM")
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .param("droneSerial", "SN-DJI-M300-88")
                .param("maxAltitudeM", "120")
                .with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.flightPermitVerificationStatus").value("PENDING_REVIEW"))
        .andExpect(jsonPath("$.data.flightPermitNumber").value("GP-2026-100"))
        .andExpect(jsonPath("$.data.flightPermitFileSha256").isNotEmpty());

    mockMvc
        .perform(get("/api/v1/inspections").with(principal(managerId, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].pendingAction").value("PERMIT_REVIEW"));

    // 5. Starting field session before Manager approval is blocked
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preFlightChecklistNote\":\"All ready\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FLIGHT_PERMIT_PENDING_REVIEW"));

    // 6. Inspector cannot verify/approve their own permit
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit/review", inspectionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
        .andExpect(status().isForbidden());

    // 7. Manager rejection without reason fails; rejection with reason blocks startSession
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit/review", inspectionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\",\"reason\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit/review", inspectionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECTED\",\"reason\":\"Stamp on page 2 is illegible\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.flightPermitVerificationStatus").value("REJECTED"))
        .andExpect(
            jsonPath("$.data.flightPermitRejectionReason").value("Stamp on page 2 is illegible"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preFlightChecklistNote\":\"All ready\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FLIGHT_PERMIT_REJECTED"));

    // 8. Inspector re-uploads clear scan -> PENDING_REVIEW, Manager approves -> startSession
    // succeeds
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .file(validPdf)
                .param("permitNumber", "GP-2026-100-REV2")
                .param("issuingAuthority", "Cục Tác chiến - BTTM")
                .param("validFrom", validFrom)
                .param("validTo", validTo)
                .param("droneSerial", "SN-DJI-M300-88")
                .param("maxAltitudeM", "120")
                .with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.flightPermitVerificationStatus").value("PENDING_REVIEW"))
        .andExpect(jsonPath("$.data.flightPermitRejectionReason").doesNotExist());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit/review", inspectionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"decision\":\"APPROVED\",\"notes\":\"Verified stamp, coordinates, and drone SN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.flightPermitVerificationStatus").value("APPROVED"))
        .andExpect(jsonPath("$.data.flightPermitVerifiedBy").value(managerId.toString()));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preFlightChecklistNote\":\"All ready\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
  }

  @Test
  void prohibitsCustomerAndManagerFlightPermitUploads() throws Exception {
    MockMultipartFile pdf =
        new MockMultipartFile(
            "file",
            "permit.pdf",
            "application/pdf",
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.UTF_8));

    for (String role : List.of("CUSTOMER", "MANAGER")) {
      UUID actor = "CUSTOMER".equals(role) ? customerId : managerId;
      mockMvc
          .perform(
              MockMvcRequestBuilders.multipart(
                      "/api/v1/inspections/{id}/flight-permit", inspectionId)
                  .file(pdf)
                  .param("permitNumber", "GP-2026-200")
                  .param("issuingAuthority", "Cục Tác chiến - BTTM")
                  .param("validFrom", plannedStart.minus(1, ChronoUnit.HOURS).toString())
                  .param("validTo", plannedEnd.plus(2, ChronoUnit.HOURS).toString())
                  .with(principal(actor, role)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void prohibitsThePermitUploaderFromVerifyingTheirOwnPermit() throws Exception {
    Inspection inspection = inspections.findById(inspectionId).orElseThrow();
    inspection.recordFlightPermit(
        "permits/legacy-manager-upload.pdf",
        "legacy-manager-upload.pdf",
        "GP-LEGACY-1",
        "Cục Tác chiến - BTTM",
        plannedStart.minus(1, ChronoUnit.HOURS),
        plannedEnd.plus(1, ChronoUnit.HOURS),
        "SN-DJI-M300-88",
        120,
        "sha256",
        1024L,
        "application/pdf",
        Instant.now(),
        managerId);
    inspections.saveAndFlush(inspection);

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit/review", inspectionId)
                .with(principal(managerId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
  }

  private static RequestPostProcessor principal(UUID subjectId, String role) {
    return jwt()
        .jwt(token -> token.subject(subjectId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
