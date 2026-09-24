package com.smartdroneinspection.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.shared.api.ApiResponse;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  void successFactoryCreatesTheCommonSuccessEnvelope() {
    var data = "inspection-123";

    var response = ApiResponse.success(data);

    assertThat(response.success()).isTrue();
    assertThat(response.message()).isEqualTo("Success");
    assertThat(response.data()).isEqualTo(data);
  }

  @Test
  void successFactoryAllowsAnEndpointSpecificMessage() {
    var response = ApiResponse.success("Evidence uploaded", "evidence-123");

    assertThat(response.success()).isTrue();
    assertThat(response.message()).isEqualTo("Evidence uploaded");
    assertThat(response.data()).isEqualTo("evidence-123");
  }
}
