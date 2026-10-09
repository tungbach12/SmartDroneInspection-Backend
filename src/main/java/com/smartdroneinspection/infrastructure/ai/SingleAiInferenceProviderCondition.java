package com.smartdroneinspection.infrastructure.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Rejects ambiguous image-inference provider configuration before either adapter is created. */
class SingleAiInferenceProviderCondition implements Condition {

  private static final String YOLO_ENABLED = "app.ai.yolo.enabled";
  private static final String COMPATIBLE_VISION_ENABLED = "app.ai.compatible-vision.enabled";
  private static final String ERROR_MESSAGE =
      "Only one advisory image inference provider may be enabled at a time.";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    boolean yoloEnabled = context.getEnvironment().getProperty(YOLO_ENABLED, Boolean.class, false);
    boolean compatibleVisionEnabled =
        context.getEnvironment().getProperty(COMPATIBLE_VISION_ENABLED, Boolean.class, false);
    if (yoloEnabled && compatibleVisionEnabled) {
      throw new IllegalStateException(ERROR_MESSAGE);
    }
    return true;
  }
}
