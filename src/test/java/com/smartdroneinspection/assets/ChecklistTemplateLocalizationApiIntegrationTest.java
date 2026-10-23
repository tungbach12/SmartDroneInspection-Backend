package com.smartdroneinspection.assets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.assets.api.ChecklistTemplateController;
import com.smartdroneinspection.assets.api.dto.response.ChecklistTemplateResponse;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.service.ChecklistTemplateService;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChecklistTemplateLocalizationApiIntegrationTest {

  private final ChecklistTemplateService service = mock(ChecklistTemplateService.class);
  private final UUID templateId = UUID.randomUUID();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ChecklistTemplateController(service)).build();
    var template =
        new ChecklistTemplate(
            "bilingual-test",
            1,
            null,
            "Bridge Basic",
            "Inspect the bridge",
            "Cầu cơ bản",
            "Kiểm tra cây cầu",
            UUID.randomUUID());
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
    template.publish();
    when(service.getActiveTemplate(templateId, Locale.forLanguageTag("vi")))
        .thenReturn(ChecklistTemplateResponse.from(template, Locale.forLanguageTag("vi")));
    when(service.getActiveTemplate(templateId, Locale.ENGLISH))
        .thenReturn(ChecklistTemplateResponse.from(template, Locale.ENGLISH));
  }

  @Test
  void returnsVietnameseManagedContentForVietnameseRequests() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/checklist-templates/{templateId}", templateId)
                .header("Accept-Language", "vi"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Cầu cơ bản"))
        .andExpect(jsonPath("$.data.description").value("Kiểm tra cây cầu"))
        .andExpect(jsonPath("$.data.items[0].sectionName").value("Kết cấu"))
        .andExpect(jsonPath("$.data.items[0].prompt").value("Kiểm tra vết nứt nhìn thấy"))
        .andExpect(jsonPath("$.data.items[0].guidance").value("Quan sát vết nứt"));
    verify(service).getActiveTemplate(templateId, Locale.forLanguageTag("vi"));
  }

  @Test
  void defaultsToEnglishWhenNoLanguageIsRequested() throws Exception {
    mockMvc
        .perform(get("/api/v1/checklist-templates/{templateId}", templateId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Bridge Basic"))
        .andExpect(jsonPath("$.data.items[0].prompt").value("Check visible surface cracks"));
    verify(service).getActiveTemplate(templateId, Locale.ENGLISH);
  }
}
