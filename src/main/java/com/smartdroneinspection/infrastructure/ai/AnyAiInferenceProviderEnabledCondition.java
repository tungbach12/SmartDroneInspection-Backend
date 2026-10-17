package com.smartdroneinspection.infrastructure.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Creates the advisory routing bean only when at least one detector is configured. With none, the
 * inspection service keeps reporting that automated analysis is not configured instead of routing
 * to a detector that does not exist.
 */
class AnyAiInferenceProviderEnabledCondition implements Condition {

  private static final String YOLO_ENABLED = "app.ai.yolo.enabled";
  private static final String COMPATIBLE_VISION_ENABLED = "app.ai.compatible-vision.enabled";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    boolean yoloEnabled = context.getEnvironment().getProperty(YOLO_ENABLED, Boolean.class, false);
    boolean compatibleVisionEnabled =
        context.getEnvironment().getProperty(COMPATIBLE_VISION_ENABLED, Boolean.class, false);
    return yoloEnabled || compatibleVisionEnabled;
  }
}
