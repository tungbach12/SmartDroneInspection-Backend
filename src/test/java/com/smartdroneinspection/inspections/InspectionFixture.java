package com.smartdroneinspection.inspections;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistItem;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.InspectionQuotation;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionRequestPriority;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reusable production-shaped data for the FE-04 inspection execution tests. */
public final class InspectionFixture {

  private final AssetCategoryRepository categories;
  private final ChecklistTemplateRepository templates;
  private final AssetRepository assets;
  private final InspectionRequestRepository requests;
  private final InspectionQuotationRepository quotations;
  private final InspectionServiceOrderRepository serviceOrders;
  private final InspectionAssignmentRepository assignments;
  private final UserRepository users;
  private final JdbcTemplate jdbcTemplate;

  public InspectionFixture(
      AssetCategoryRepository categories,
      ChecklistTemplateRepository templates,
      AssetRepository assets,
      InspectionRequestRepository requests,
      InspectionQuotationRepository quotations,
      InspectionServiceOrderRepository serviceOrders,
      InspectionAssignmentRepository assignments,
      UserRepository users,
      JdbcTemplate jdbcTemplate) {
    this.categories = categories;
    this.templates = templates;
    this.assets = assets;
    this.requests = requests;
    this.quotations = quotations;
    this.serviceOrders = serviceOrders;
    this.assignments = assignments;
    this.users = users;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Data create() {
    UUID organizationId = createOrganization("Primary");
    UUID otherOrganizationId = createOrganization("Other");

    User client =
        saveUser(
            "client",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId,
            UserRole.CLIENT);
    User manager =
        saveUser(
            "manager",
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null,
            UserRole.SERVICE_MANAGER);
    User inspector =
        saveUser(
            "inspector", UserStatus.ACTIVE, ActorZone.SERVICE_WORKFORCE, null, UserRole.INSPECTOR);
    User otherInspector =
        saveUser(
            "other-inspector",
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null,
            UserRole.INSPECTOR);
    User otherOrganizationClient =
        saveUser(
            "other-client",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            otherOrganizationId,
            UserRole.CLIENT);
    User otherOrganizationInspector =
        saveUser(
            "other-organization-inspector",
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null,
            UserRole.INSPECTOR);
    User inactiveInspector =
        saveUser(
            "inactive-inspector",
            UserStatus.DISABLED,
            ActorZone.SERVICE_WORKFORCE,
            null,
            UserRole.INSPECTOR);

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory("bridge-" + organizationId, "Bridge", "Bridge infrastructure", true));
    ChecklistTemplate template =
        new ChecklistTemplate(
            "bridge-inspection-" + organizationId,
            1,
            category.getId(),
            "Bridge inspection",
            "FE-04 fixture checklist",
            manager.getId());
    ChecklistItem requiredItem =
        template.addItem(
            "surface-condition",
            "Surface",
            "Check the visible surface condition",
            ChecklistResponseType.PASS_FAIL,
            true,
            0,
            null,
            null);
    template.publish();
    templates.saveAndFlush(template);

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                organizationId,
                category.getId(),
                "asset-" + organizationId,
                "North bridge",
                "Primary fixture asset",
                "District 1",
                null,
                null,
                null,
                client.getId()));
    InspectionRequest request =
        InspectionRequest.adHoc(
            organizationId,
            asset.getId(),
            template.getId(),
            client.getId(),
            "Ad hoc bridge inspection",
            InspectionRequestPriority.NORMAL,
            Instant.now().plusSeconds(86_400),
            null,
            "Site contact",
            null,
            null,
            null);
    request.submit();
    requests.saveAndFlush(request);

    InspectionQuotation quotation =
        new InspectionQuotation(
            UUID.randomUUID(),
            request.getId(),
            1,
            null,
            manager.getId(),
            "USD",
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            BigDecimal.valueOf(100),
            "{\"items\":[]}",
            "{\"scope\":\"bridge\"}",
            BigDecimal.ONE,
            "NET 30");
    quotation.send();
    quotation.approve(client.getId());
    quotations.saveAndFlush(quotation);

    InspectionServiceOrder order =
        serviceOrders.saveAndFlush(
            InspectionServiceOrder.fromApprovedQuotation(
                quotation,
                "SO-" + organizationId,
                manager.getId(),
                "{\"report\":\"Final report\"}"));
    InspectionAssignment assignment =
        new InspectionAssignment(
            order.getId(),
            inspector.getId(),
            manager.getId(),
            Instant.now().plusSeconds(86_400),
            "Call site contact before entry");
    assignment.accept();
    assignments.saveAndFlush(assignment);

    Asset otherOrganizationAsset =
        assets.saveAndFlush(
            new Asset(
                otherOrganizationId,
                category.getId(),
                "asset-" + otherOrganizationId,
                "South bridge",
                "Other organization fixture asset",
                "District 2",
                null,
                null,
                null,
                otherOrganizationClient.getId()));
    InspectionRequest otherOrganizationRequest =
        InspectionRequest.adHoc(
            otherOrganizationId,
            otherOrganizationAsset.getId(),
            template.getId(),
            otherOrganizationClient.getId(),
            "Ad hoc bridge inspection",
            InspectionRequestPriority.NORMAL,
            Instant.now().plusSeconds(86_400),
            null,
            "Other site contact",
            null,
            null,
            null);
    otherOrganizationRequest.submit();
    requests.saveAndFlush(otherOrganizationRequest);

    InspectionQuotation otherOrganizationQuotation =
        new InspectionQuotation(
            UUID.randomUUID(),
            otherOrganizationRequest.getId(),
            1,
            null,
            manager.getId(),
            "USD",
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            BigDecimal.valueOf(100),
            "{\"items\":[]}",
            "{\"scope\":\"bridge\"}",
            BigDecimal.ONE,
            "NET 30");
    otherOrganizationQuotation.send();
    otherOrganizationQuotation.approve(otherOrganizationClient.getId());
    quotations.saveAndFlush(otherOrganizationQuotation);

    InspectionServiceOrder otherOrganizationOrder =
        serviceOrders.saveAndFlush(
            InspectionServiceOrder.fromApprovedQuotation(
                otherOrganizationQuotation,
                "SO-" + otherOrganizationId,
                manager.getId(),
                "{\"report\":\"Final report\"}"));
    InspectionAssignment otherOrganizationAssignment =
        new InspectionAssignment(
            otherOrganizationOrder.getId(),
            otherOrganizationInspector.getId(),
            manager.getId(),
            Instant.now().plusSeconds(86_400),
            "Call the other site contact before entry");
    otherOrganizationAssignment.accept();
    assignments.saveAndFlush(otherOrganizationAssignment);

    return new Data(
        organizationId,
        otherOrganizationId,
        client.getId(),
        otherOrganizationClient.getId(),
        manager.getId(),
        inspector.getId(),
        otherInspector.getId(),
        otherOrganizationInspector.getId(),
        inactiveInspector.getId(),
        category.getId(),
        template.getId(),
        requiredItem.getId(),
        asset.getId(),
        request.getId(),
        order.getId(),
        assignment.getId(),
        otherOrganizationAsset.getId(),
        otherOrganizationRequest.getId(),
        otherOrganizationOrder.getId(),
        otherOrganizationAssignment.getId());
  }

  private UUID createOrganization(String label) {
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        label + " organization " + organizationId,
        "ORG-" + organizationId);
    return organizationId;
  }

  private User saveUser(
      String label, UserStatus status, ActorZone actorZone, UUID organizationId, UserRole role) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            status,
            actorZone,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  public record Data(
      UUID organizationId,
      UUID otherOrganizationId,
      UUID clientId,
      UUID otherOrganizationClientId,
      UUID managerId,
      UUID inspectorId,
      UUID otherInspectorId,
      UUID otherOrganizationInspectorId,
      UUID inactiveInspectorId,
      UUID categoryId,
      UUID checklistTemplateId,
      UUID checklistItemId,
      UUID assetId,
      UUID requestId,
      UUID serviceOrderId,
      UUID assignmentId,
      UUID otherOrganizationAssetId,
      UUID otherOrganizationRequestId,
      UUID otherOrganizationOrderId,
      UUID otherOrganizationAssignmentId) {}
}
