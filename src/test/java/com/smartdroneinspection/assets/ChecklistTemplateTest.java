package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChecklistTemplateTest {

  @Test
  void publishesOnlyWhenAtLeastOneItemExists() {
    ChecklistTemplate template =
        new ChecklistTemplate("bridge-basic", 1, null, "Bridge Basic", null, UUID.randomUUID());

    assertThatThrownBy(template::publish)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Checklist template must contain at least one item");

    template.addItem(
        "surface-crack",
        null,
        "Check visible surface cracks",
        ChecklistResponseType.PASS_FAIL,
        true,
        0,
        null,
        null);
    template.publish();

    assertThat(template.getTemplateKey()).isEqualTo("BRIDGE-BASIC");
    assertThat(template.getStatus()).isEqualTo(ChecklistTemplateStatus.ACTIVE);
    assertThat(template.getPublishedAt()).isNotNull();
  }

  @Test
  void activeTemplateCannotReceiveNewItems() {
    ChecklistTemplate template =
        new ChecklistTemplate("bridge-basic", 1, null, "Bridge Basic", null, UUID.randomUUID());
    template.addItem(
        "surface-crack",
        null,
        "Check visible surface cracks",
        ChecklistResponseType.PASS_FAIL,
        true,
        0,
        null,
        null);
    template.publish();

    assertThatThrownBy(
            () ->
                template.addItem(
                    "corrosion",
                    null,
                    "Check corrosion",
                    ChecklistResponseType.PASS_FAIL,
                    true,
                    1,
                    null,
                    null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Published checklist template is immutable");
  }
}
