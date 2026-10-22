package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import java.util.Locale;
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

  @Test
  void returnsVietnameseSystemContentWhenAvailableAndEnglishOtherwise() {
    ChecklistTemplate template =
        new ChecklistTemplate(
            "bridge-basic",
            1,
            null,
            "Bridge Basic",
            "Inspect the bridge",
            "Cầu cơ bản",
            "Kiểm tra cây cầu",
            UUID.randomUUID());
    var item =
        template.addItem(
            "surface-crack",
            "Structure",
            "Check visible surface cracks",
            "Kết cấu",
            "Kiểm tra vết nứt nhìn thấy",
            ChecklistResponseType.PASS_FAIL,
            true,
            0,
            "Look for cracks",
            "Quan sát vết nứt",
            null);

    assertThat(template.localizedName(Locale.forLanguageTag("vi-VN"))).isEqualTo("Cầu cơ bản");
    assertThat(template.localizedDescription(Locale.forLanguageTag("vi")))
        .isEqualTo("Kiểm tra cây cầu");
    assertThat(item.localizedSectionName(Locale.forLanguageTag("vi"))).isEqualTo("Kết cấu");
    assertThat(item.localizedSectionName(Locale.ENGLISH)).isEqualTo("Structure");
    assertThat(item.localizedPrompt(Locale.forLanguageTag("vi")))
        .isEqualTo("Kiểm tra vết nứt nhìn thấy");
    assertThat(item.localizedGuidance(Locale.forLanguageTag("vi"))).isEqualTo("Quan sát vết nứt");
    assertThat(item.localizedPrompt(Locale.ENGLISH)).isEqualTo("Check visible surface cracks");
    assertThat(item.localizedGuidance(Locale.forLanguageTag("vi"))).isEqualTo("Quan sát vết nứt");
  }

  @Test
  void existingEnglishOnlyContentFallsBackToEnglishForVietnameseRequests() {
    ChecklistTemplate template =
        new ChecklistTemplate("bridge-basic", 1, null, "Bridge Basic", null, UUID.randomUUID());
    var item =
        template.addItem(
            "surface-crack",
            null,
            "Check visible surface cracks",
            ChecklistResponseType.PASS_FAIL,
            true,
            0,
            null,
            null);

    assertThat(template.localizedName(Locale.forLanguageTag("vi"))).isEqualTo("Bridge Basic");
    assertThat(item.localizedPrompt(Locale.forLanguageTag("vi")))
        .isEqualTo("Check visible surface cracks");
  }
}
