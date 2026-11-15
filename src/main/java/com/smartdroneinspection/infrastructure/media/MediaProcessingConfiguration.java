package com.smartdroneinspection.infrastructure.media;

import com.smartdroneinspection.inspections.spi.VideoTranscodePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProcessingProperties.class)
@ConditionalOnProperty(
    prefix = "app.evidence.processing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class MediaProcessingConfiguration {

  @Bean
  VideoTranscodePort videoTranscodePort(
      MediaProcessingProperties properties, ObjectMapper objectMapper) {
    return new FfmpegVideoTranscoder(properties, objectMapper);
  }
}
