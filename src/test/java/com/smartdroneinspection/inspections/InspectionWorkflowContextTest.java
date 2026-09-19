package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.InspectionAssignment;
import com.smartdroneinspection.inspections.domain.InspectionQuotation;
import com.smartdroneinspection.inspections.domain.InspectionRequest;
import com.smartdroneinspection.inspections.domain.InspectionRequestAttachment;
import com.smartdroneinspection.inspections.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspections.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspections.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspections.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspections.repository.InspectionServiceOrderRepository;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionWorkflowContextTest {

  @Autowired ApplicationContext applicationContext;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired InspectionRequestRepository requestRepository;
  @Autowired InspectionQuotationRepository quotationRepository;
  @Autowired InspectionServiceOrderRepository serviceOrderRepository;
  @Autowired InspectionAssignmentRepository assignmentRepository;

  @Value("${spring.jpa.hibernate.ddl-auto}")
  String ddlAuto;

  @Test
  void startsWithValidateAndDiscoversWf2Persistence() {
    assertThat(ddlAuto).isEqualTo("validate");
    assertThat(requestRepository).isNotNull();
    assertThat(quotationRepository).isNotNull();
    assertThat(serviceOrderRepository).isNotNull();
    assertThat(assignmentRepository).isNotNull();

    assertThat(entityManagerFactory.getMetamodel().getEntities())
        .extracting(entityType -> entityType.getJavaType().getSimpleName())
        .contains(
            "InspectionRequest",
            "InspectionRequestAttachment",
            "InspectionQuotation",
            "InspectionServiceOrder",
            "InspectionAssignment");
  }

  @Test
  void wf2EntitiesDoNotImportOtherFeatureEntities() {
    List<Class<?>> entities =
        List.of(
            InspectionRequest.class,
            InspectionRequestAttachment.class,
            InspectionQuotation.class,
            InspectionServiceOrder.class,
            InspectionAssignment.class);

    assertThat(entities.stream().flatMap(type -> List.of(type.getDeclaredFields()).stream()))
        .allMatch(this::isLocalOrScalarField);
  }

  private boolean isLocalOrScalarField(Field field) {
    String packageName = field.getType().getPackageName();
    return !packageName.startsWith("com.smartdroneinspection.assets")
        && !packageName.startsWith("com.smartdroneinspection.users");
  }
}
