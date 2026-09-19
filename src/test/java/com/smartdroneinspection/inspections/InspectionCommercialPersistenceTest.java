package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.InspectionQuotation;
import com.smartdroneinspection.inspections.domain.InspectionRequest;
import com.smartdroneinspection.inspections.domain.InspectionRequestPriority;
import com.smartdroneinspection.inspections.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspections.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspections.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspections.repository.InspectionServiceOrderRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionCommercialPersistenceTest {

  @Autowired InspectionRequestRepository requestRepository;
  @Autowired InspectionQuotationRepository quotationRepository;
  @Autowired InspectionServiceOrderRepository serviceOrderRepository;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void reloadsJsonSnapshotsAndNormalizesPersistedCurrency() throws Exception {
    Fixture fixture = persistFixture();
    InspectionQuotation quotation = persistQuotation(fixture, UUID.randomUUID(), 1, " usd ");
    entityManager.clear();

    InspectionQuotation reloaded = quotationRepository.findById(quotation.getId()).orElseThrow();

    assertThat(reloaded.getCurrency()).isEqualTo("USD");
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(reloaded.getPricingDetails()))
        .isEqualTo(objectMapper.readTree("{\"items\":[]}"));
    assertThat(objectMapper.readTree(reloaded.getScopeSnapshot()))
        .isEqualTo(objectMapper.readTree("{\"scope\":\"tower\"}"));
  }

  @Test
  void rejectsDuplicateQuotationSeriesVersion() {
    Fixture fixture = persistFixture();
    UUID seriesId = UUID.randomUUID();
    persistQuotation(fixture, seriesId, 1, "USD");

    assertThatThrownBy(
            () -> quotationRepository.saveAndFlush(newQuotation(fixture, seriesId, 1, "USD")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsNegativeAmountAtDatabaseBoundary() {
    Fixture fixture = persistFixture();
    UUID quotationId = UUID.randomUUID();
    UUID seriesId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO inspection_quotations (
                      id, quotation_series_id, inspection_request_id, version_number,
                      prepared_by_user_id, currency, subtotal, tax_amount, total_amount,
                      pricing_details, scope_snapshot, payment_terms, status
                    ) VALUES (?, ?, ?, 1, ?, 'USD', -1, 0, 0, '{}'::jsonb, '{}'::jsonb, 'NET 30', 'DRAFT')
                    """,
                    quotationId,
                    seriesId,
                    fixture.requestId(),
                    fixture.userId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void onlyOneServiceOrderCanUseApprovedQuotation() {
    Fixture fixture = persistFixture();
    InspectionQuotation quotation = persistQuotation(fixture, UUID.randomUUID(), 1, "USD");
    quotation.send();
    quotation.approve(fixture.userId());
    quotationRepository.saveAndFlush(quotation);

    InspectionServiceOrder first =
        InspectionServiceOrder.fromApprovedQuotation(
            quotation, "SO-2026-0001", fixture.userId(), "{\"report\":\"Final report\"}");
    serviceOrderRepository.saveAndFlush(first);

    InspectionServiceOrder second =
        InspectionServiceOrder.fromApprovedQuotation(
            quotation, "SO-2026-0002", fixture.userId(), "{\"report\":\"Replacement report\"}");

    assertThatThrownBy(() -> serviceOrderRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private InspectionQuotation persistQuotation(
      Fixture fixture, UUID seriesId, int version, String currency) {
    return quotationRepository.saveAndFlush(newQuotation(fixture, seriesId, version, currency));
  }

  private InspectionQuotation newQuotation(
      Fixture fixture, UUID seriesId, int version, String currency) {
    return new InspectionQuotation(
        seriesId,
        fixture.requestId(),
        version,
        null,
        fixture.userId(),
        currency,
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(110),
        "{\"items\":[]}",
        "{\"scope\":\"tower\"}",
        BigDecimal.valueOf(2),
        "NET 30");
  }

  private Fixture persistFixture() {
    UUID organizationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID checklistTemplateId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    String suffix = organizationId.toString();

    jdbcTemplate.update(
        "INSERT INTO organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        "Organization " + suffix,
        "ORG-" + suffix);
    String email = "quotation-user-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?, 0, 0, FALSE)
        """,
        userId,
        email,
        email,
        "Quotation User",
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO asset_categories (id, code, name) VALUES (?, ?, ?)",
        categoryId,
        "CATEGORY-" + suffix.toUpperCase(Locale.ROOT),
        "Category " + suffix);
    jdbcTemplate.update(
        """
        INSERT INTO checklist_templates (
          id, template_key, version_number, asset_category_id, name, status, created_by_user_id
        ) VALUES (?, ?, 1, ?, ?, 'DRAFT', ?)
        """,
        checklistTemplateId,
        "CHECKLIST-" + suffix.toUpperCase(Locale.ROOT),
        categoryId,
        "Bridge Checklist",
        userId);
    jdbcTemplate.update(
        """
        INSERT INTO assets (
          id, organization_id, category_id, code, name, location_text,
          status, created_by_user_id
        ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
        """,
        assetId,
        organizationId,
        categoryId,
        "ASSET-" + suffix.toUpperCase(Locale.ROOT),
        "Bridge",
        "District 1",
        userId);
    InspectionRequest request =
        requestRepository.saveAndFlush(
            InspectionRequest.adHoc(
                organizationId,
                assetId,
                checklistTemplateId,
                userId,
                "Ad hoc tower inspection",
                InspectionRequestPriority.NORMAL,
                null,
                null,
                null,
                null,
                null,
                null));
    return new Fixture(organizationId, userId, request.getId());
  }

  private record Fixture(UUID organizationId, UUID userId, UUID requestId) {}
}
