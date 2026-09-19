package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistResponseType;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ChecklistTemplatePersistenceTest {

  @Autowired AssetCategoryRepository assetCategoryRepository;
  @Autowired ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void persistsTemplateVersionWithOrderedItems() {
    AssetCategory category =
        assetCategoryRepository.saveAndFlush(
            new AssetCategory("bridges", "Bridges", "Bridge infrastructure", true));
    UUID createdByUserId = persistCreator();
    ChecklistTemplate template =
        new ChecklistTemplate(
            "bridge-basic", 1, category.getId(), "Bridge Basic", null, createdByUserId);
    template.addItem(
        "surface-crack",
        "Surface",
        "Check visible surface cracks",
        ChecklistResponseType.PASS_FAIL,
        true,
        0,
        null,
        null);
    template.addItem(
        "joint-condition",
        "Surface",
        "Check joint condition",
        ChecklistResponseType.CHOICE,
        true,
        1,
        null,
        "{\"choices\":[\"GOOD\",\"POOR\"]}");

    ChecklistTemplate saved = checklistTemplateRepository.saveAndFlush(template);
    entityManager.clear();

    ChecklistTemplate reloaded =
        checklistTemplateRepository.findDetailedById(saved.getId()).orElseThrow();

    assertThat(reloaded.getTemplateKey()).isEqualTo("BRIDGE-BASIC");
    assertThat(reloaded.getVersionNumber()).isEqualTo(1);
    assertThat(reloaded.getItems())
        .extracting(item -> item.getDisplayOrder())
        .containsExactly(0, 1);
    assertThat(reloaded.getItems()).hasSize(2);
  }

  @Test
  void rejectsDuplicateTemplateKeyAndVersionNumber() {
    UUID createdByUserId = persistCreator();
    checklistTemplateRepository.saveAndFlush(
        new ChecklistTemplate("duplicate-bridge", 1, null, "Bridge Basic", null, createdByUserId));

    assertThatThrownBy(
            () ->
                checklistTemplateRepository.saveAndFlush(
                    new ChecklistTemplate(
                        "duplicate-bridge",
                        1,
                        null,
                        "Bridge Basic Revision",
                        null,
                        createdByUserId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID persistCreator() {
    UUID creatorId = UUID.randomUUID();
    String email = "creator-" + creatorId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'PLATFORM', 0, 0, FALSE)
        """,
        creatorId,
        email,
        email,
        "Checklist Creator");
    return creatorId;
  }
}
