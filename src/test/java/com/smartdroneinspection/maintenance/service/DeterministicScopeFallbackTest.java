package com.smartdroneinspection.maintenance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeterministicScopeFallbackTest {

  private DeterministicScopeFallback fallback;

  @BeforeEach
  void setUp() {
    fallback = new DeterministicScopeFallback();
  }

  @Test
  void generatesConcreteCrackFallback() {
    SuggestedScope result =
        fallback.fallback("Vết nứt bê tông", "Nứt dọc dầm chính", "Dầm T", "bề rộng 0.35mm");

    assertThat(result.correctiveScope()).contains("bơm keo epoxy");
    assertThat(result.acceptanceCriteria()).contains("TCVN 9349").contains("1.5 MPa");
    assertThat(result.referenceStandards()).contains("TCVN 9343:2012");
    assertThat(result.suggestedTasks()).isNotEmpty();
  }

  @Test
  void generatesSpallingFallback() {
    SuggestedScope result =
        fallback.fallback("Spalling", "Bê tông bị bong tróc vỡ mảng", "Trụ cầu", "40x30cm");

    assertThat(result.correctiveScope()).contains("đục tẩy").contains("vữa sửa chữa");
    assertThat(result.acceptanceCriteria()).contains("1.5 MPa");
    assertThat(result.referenceStandards()).contains("EN 1504-9");
    assertThat(result.suggestedTasks()).isNotEmpty();
  }

  @Test
  void generatesSteelCorrosionFallback() {
    SuggestedScope result =
        fallback.fallback("Corrosion", "Thanh giằng thép bị gỉ sét bề mặt", "Thanh giằng", "15% section loss");

    assertThat(result.correctiveScope()).contains("sơn").contains("Sa 2.5");
    assertThat(result.acceptanceCriteria()).contains("225 um").contains("ISO 2409");
    assertThat(result.referenceStandards()).contains("ISO 12944-5");
    assertThat(result.suggestedTasks()).isNotEmpty();
  }

  @Test
  void generatesWaterLeakageFallback() {
    SuggestedScope result =
        fallback.fallback("Thấm rò rỉ nước", "Nước rỉ qua mạch ngừng", "Mố cầu", "dòng chảy liên tục");

    assertThat(result.correctiveScope()).contains("Polyurethane");
    assertThat(result.acceptanceCriteria()).contains("72 giờ").contains("khô ráo");
    assertThat(result.referenceStandards()).contains("TCVN 9343:2012");
    assertThat(result.suggestedTasks()).isNotEmpty();
  }

  @Test
  void generatesGeneralFallbackWhenUnmatched() {
    SuggestedScope result =
        fallback.fallback("Khuyết tật không xác định", "Cần kiểm tra thêm", "Cấu kiện X", null);

    assertThat(result.correctiveScope()).isNotBlank();
    assertThat(result.acceptanceCriteria()).isNotBlank();
    assertThat(result.referenceStandards()).isNotEmpty();
    assertThat(result.suggestedTasks()).isNotEmpty();
  }
}
