package com.smartdroneinspection.assets;

import com.smartdroneinspection.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AssetPlanningContextTest {

  @Test
  void contextLoadsWithHibernateSchemaValidation() {}
}
