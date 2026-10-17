package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the advisory detector the inspection service receives.
 *
 * <p>The self-hosted YOLO detector is the default and the compatible vision endpoint is the option
 * behind it, so both may be enabled at once: the option answers only for images the default
 * detector could not process.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(AnyAiInferenceProviderEnabledCondition.class)
class AiInferenceRoutingConfiguration {

  @Bean
  @Primary
  AiInferencePort advisoryInferencePort(
      ObjectProvider<YoloInferenceClient> yolo,
      ObjectProvider<OpenAiCompatibleInferenceClient> compatibleVision) {
    YoloInferenceClient detector = yolo.getIfAvailable();
    OpenAiCompatibleInferenceClient vision = compatibleVision.getIfAvailable();
    if (detector != null) {
      return new PrimaryWithFallbackInference(detector, Optional.ofNullable(vision));
    }
    return new PrimaryWithFallbackInference(
        Objects.requireNonNull(vision, "A configured detector is required"), Optional.empty());
  }
}
