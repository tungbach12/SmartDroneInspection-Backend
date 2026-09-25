package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionFixtureTest {

  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired InspectionRequestRepository requests;
  @Autowired InspectionQuotationRepository quotations;
  @Autowired InspectionServiceOrderRepository serviceOrders;
  @Autowired InspectionAssignmentRepository assignments;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void preservesOrganizationAndAssigneeScopeInFixture() {
    InspectionFixture.Data data = fixture().create();

    assertThat(assets.findById(data.assetId()).orElseThrow().getOrganizationId())
        .isEqualTo(data.organizationId());
    assertThat(assets.findById(data.otherOrganizationAssetId()).orElseThrow().getOrganizationId())
        .isEqualTo(data.otherOrganizationId());
    assertThat(requests.findById(data.requestId()).orElseThrow().getOrganizationId())
        .isEqualTo(data.organizationId());
    assertThat(
            requests.findById(data.otherOrganizationRequestId()).orElseThrow().getOrganizationId())
        .isEqualTo(data.otherOrganizationId());
    assertThat(assignments.findById(data.assignmentId()).orElseThrow().getInspectorUserId())
        .isEqualTo(data.inspectorId());
    assertThat(
            assignments
                .findById(data.otherOrganizationAssignmentId())
                .orElseThrow()
                .getInspectorUserId())
        .isEqualTo(data.otherOrganizationInspectorId());
    assertThat(data.otherOrganizationId()).isNotEqualTo(data.organizationId());
    assertThat(users.findById(data.inspectorId()).orElseThrow().roleValues())
        .containsExactly("INSPECTOR");
    assertThat(users.findById(data.clientId()).orElseThrow().getOrganizationId())
        .isEqualTo(data.organizationId());
    assertThat(users.findById(data.otherOrganizationClientId()).orElseThrow().getOrganizationId())
        .isEqualTo(data.otherOrganizationId());
  }

  private InspectionFixture fixture() {
    return new InspectionFixture(
        categories,
        templates,
        assets,
        requests,
        quotations,
        serviceOrders,
        assignments,
        users,
        jdbcTemplate);
  }
}
