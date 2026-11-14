package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ServicePaymentSchemaMigrationTest {

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired ServiceRequestRepository requests;
  @Autowired ServiceRequestOccurrenceRepository occurrences;
  @Autowired InspectionRepository inspections;

  @Test
  void createsDedicatedPaymentTablesAndRemovesFinanceColumnsFromOccurrence() {
    jdbcTemplate.queryForObject(
        "SELECT version FROM flyway_schema_history WHERE version = '29'", String.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name IN ('service_invoices', 'service_payments', 'inspector_payouts')",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'service_request_occurrences' AND column_name IN ('total_price', 'payment_status', 'payos_order_code', 'payout_status')",
                Integer.class))
        .isZero();
    assertColumns(
        "service_invoices", "occurrence_id", "total_price", "vat_rate", "report_accepted_at");
    assertColumns(
        "service_payments",
        "occurrence_id",
        "payment_status",
        "payos_order_code",
        "payment_method");
    assertColumns(
        "inspector_payouts",
        "occurrence_id",
        "payout_status",
        "payout_reference",
        "payout_recorded_at");
  }

  @Test
  void persistsInvoicePaymentAndPayoutAlongsideScheduledOccurrence() {
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, display_name, registration_code, status) VALUES (?, ?, ?, 'ACTIVE')",
        organizationId,
        "Payment Test Organization",
        "PAY-ORG-" + organizationId.toString().substring(0, 8));
    User customer =
        users.saveAndFlush(
            new User(
                "payment-test-" + UUID.randomUUID() + "@example.test",
                "Payment Test Customer",
                "test-password-hash",
                UserStatus.ACTIVE,
                ActorZone.CUSTOMER_ORGANIZATION,
                organizationId));
    User inspector =
        users.saveAndFlush(
            new User(
                "payment-inspector-" + UUID.randomUUID() + "@example.test",
                "Payment Test Inspector",
                "test-password-hash",
                UserStatus.ACTIVE,
                ActorZone.INSPECTOR_PARTNER,
                null));
    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8), "Payment Test", null, true));
    Asset asset =
        assets.saveAndFlush(
            new Asset(
                organizationId,
                category.getId(),
                "PAY-" + UUID.randomUUID().toString().substring(0, 8),
                "Payment Test Asset",
                null,
                "{}",
                null,
                null,
                null,
                java.util.List.of("Structure"),
                customer.getId()));
    ServiceRequest request =
        requests.saveAndFlush(
            new ServiceRequest(
                organizationId,
                asset.getId(),
                customer.getId(),
                "Payment Test Request",
                LocalDate.now(),
                LocalDate.now(),
                "UTC",
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                ServiceRequest.RequestType.ONE_TIME,
                null,
                null,
                null,
                null));
    Drone drone =
        drones.saveAndFlush(
            Drone.createInspectorOwned(
                inspector.getId(),
                "PAY-DRONE-" + UUID.randomUUID().toString().substring(0, 8),
                "Test Drone",
                "Test Manufacturer",
                "PAY-REG-" + UUID.randomUUID().toString().substring(0, 8),
                "payment-test/registration.pdf",
                Instant.now().plusSeconds(365L * 24 * 60 * 60)));
    Instant start = Instant.now().plusSeconds(3600);
    Inspection inspection =
        inspections.saveAndFlush(
            new Inspection(
                organizationId,
                asset.getId(),
                inspector.getId(),
                drone.getId(),
                start,
                start.plusSeconds(3600)));
    inspection.markPartnerAssignment();
    inspections.saveAndFlush(inspection);
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(request.getId(), 1, start, start.plusSeconds(3600));
    occurrence.assign(inspector.getId(), drone.getId(), inspection.getId(), customer.getId());
    occurrence.snapshotFinance(
        new BigDecimal("11000000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("15.00"),
        new BigDecimal("10.00"));
    occurrence.acceptReport();
    occurrence.assignPayOSOrder(9922334455L);
    occurrence.confirmOnlinePayment("PAY-TEST-REF", new BigDecimal("11000000.00"), "PAYOS");
    occurrence.recordPayout("BANK-TRANSFER-REF", customer.getId());
    occurrences.saveAndFlush(occurrence);

    UUID occurrenceId = occurrence.getId();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT total_price FROM service_invoices WHERE occurrence_id = ?",
                BigDecimal.class,
                occurrenceId))
        .isEqualByComparingTo("11000000.00");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payment_status FROM service_payments WHERE occurrence_id = ?",
                String.class,
                occurrenceId))
        .isEqualTo("PAID");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payos_order_code FROM service_payments WHERE occurrence_id = ?",
                Long.class,
                occurrenceId))
        .isEqualTo(9922334455L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT inspector_id FROM inspector_payouts WHERE occurrence_id = ?",
                UUID.class,
                occurrenceId))
        .isEqualTo(inspector.getId());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payout_reference FROM inspector_payouts WHERE occurrence_id = ?",
                String.class,
                occurrenceId))
        .isEqualTo("BANK-TRANSFER-REF");
  }

  private void assertColumns(String table, String... expectedColumns) {
    var columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
            String.class,
            table);
    assertThat(columns).as("columns for %s", table).contains(expectedColumns);
  }
}
