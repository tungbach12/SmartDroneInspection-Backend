package com.smartdroneinspection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class SmartDroneInspectionApplication {

  public static void main(String[] args) {
    loadLocalDotEnvIfPresent();
    SpringApplication.run(SmartDroneInspectionApplication.class, args);
  }

  private static void loadLocalDotEnvIfPresent() {
    Path envFile = Path.of(".env");
    if (!Files.isRegularFile(envFile)) {
      return;
    }
    try {
      List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
      for (String rawLine : lines) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = line.substring(0, eq).trim();
        String value = line.substring(eq + 1).trim();
        if (!key.isEmpty() && System.getProperty(key) == null && System.getenv(key) == null) {
          System.setProperty(key, value);
        }
      }
    } catch (IOException ignored) {
      // Fall back to standard environment and application.yml defaults
    }
  }
}
