package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.ChecklistResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.PeerReview;
import com.smartdroneinspection.inspections.domain.ReportVersion;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.maintenance.domain.Invoice;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssessment;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssignment;
import com.smartdroneinspection.maintenance.domain.MaintenanceChangeRequest;
import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceQuotation;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicketFinding;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.SecurityAuditEvent;
import java.util.List;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;

class PersistenceEntityMappingTest {

  private static final List<Class<?>> FEATURE_ENTITIES =
      List.of(
          Organization.class,
          SecurityAuditEvent.class,
          Inspection.class,
          ChecklistResponse.class,
          Evidence.class,
          AiFindingCandidate.class,
          VerifiedFinding.class,
          InspectionReport.class,
          ReportVersion.class,
          PeerReview.class,
          MaintenanceTicket.class,
          MaintenanceAssignment.class,
          MaintenanceAssessment.class,
          MaintenanceQuotation.class,
          MaintenanceOrder.class,
          MaintenanceWorkLog.class,
          MaintenanceChangeRequest.class,
          MaintenanceTicketFinding.class,
          Invoice.class,
          Notification.class);

  @Test
  void allFeatureEntitiesBuildValidHibernateMetadataWithoutDatabaseAccess() {
    StandardServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
    try {
      MetadataSources sources = new MetadataSources(registry);
      FEATURE_ENTITIES.forEach(sources::addAnnotatedClass);
      Metadata metadata = sources.buildMetadata();

      assertThat(metadata.getEntityBindings()).hasSize(FEATURE_ENTITIES.size());
      assertThat(metadata.getEntityBindings())
          .allSatisfy(binding -> assertThat(binding.getTable()).isNotNull());
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
