package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class CompatibleVisionDroneSpecExtractorTest {

  @Test
  void parsesStructuredJsonAndNormalizesUnitsAndVietnameseDates() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://vision.local");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://vision.local/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "message": {
                        "content": "```json\\n{\\"model\\":\\"DJI Matrice 350 RTK\\",\\"manufacturer\\":\\"DJI\\",\\"serialNumber\\":\\"1581F67G890\\",\\"registrationNumber\\":\\"DK-2026-889\\",\\"registrationExpiresAt\\":\\"05/08/2027\\",\\"mtowKg\\":\\"9.2\\",\\"maxFlightTimeMinutes\\":55,\\"maxWindResistanceMps\\":12,\\"maxAltitudeMeters\\":7000,\\"opticalZoomLevel\\":\\"23x\\",\\"cameraSensor\\":\\"48 MP\\",\\"features\\":[\\"RTK_POSITIONING\\",\\"UNKNOWN_CODE\\"]}\\n```"
                      }
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    CompatibleVisionDroneSpecExtractor extractor =
        new CompatibleVisionDroneSpecExtractor(
            builder.build(), "secret", "gpt-4o-mini", new ObjectMapper());

    var res = extractor.extract(new byte[] {1, 2}, "image/png", "");

    assertThat(res.model()).isEqualTo("DJI Matrice 350 RTK");
    assertThat(res.manufacturer()).isEqualTo("DJI");
    assertThat(res.serialNumber()).isEqualTo("1581F67G890");
    assertThat(res.registrationNumber()).isEqualTo("DK-2026-889");
    assertThat(res.registrationExpiresAt()).isEqualTo(LocalDate.of(2027, 8, 5));
    assertThat(res.mtowKg()).isEqualByComparingTo(new BigDecimal("9.2"));
    assertThat(res.maxFlightTimeMinutes()).isEqualTo(55);
    assertThat(res.opticalZoomLevel()).isEqualTo("23");
    assertThat(res.features()).containsExactly("RTK_POSITIONING");
    server.verify();
  }
}
