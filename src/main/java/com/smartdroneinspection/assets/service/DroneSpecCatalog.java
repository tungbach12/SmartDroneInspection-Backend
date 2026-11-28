package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.DronePresetResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Official airframe specification presets used for instant form pre-fill and catalog enrichment.
 */
public final class DroneSpecCatalog {

  private static final List<PresetEntry> PRESETS =
      List.of(
          new PresetEntry(
              new DronePresetResponse(
                  "dji-m350-rtk",
                  "DJI Matrice 350 RTK",
                  "DJI",
                  new BigDecimal("9.20"),
                  55,
                  new BigDecimal("12.0"),
                  new BigDecimal("7000"),
                  "23",
                  "Zenmuse H20T / H30T (48 MP)",
                  List.of(
                      "RTK_POSITIONING",
                      "THERMAL_CAMERA",
                      "OBSTACLE_AVOIDANCE",
                      "FAILSAFE_RTH",
                      "WEATHERPROOF_IP")),
              List.of("matrice 350", "m350 rtk", "m350")),
          new PresetEntry(
              new DronePresetResponse(
                  "dji-m300-rtk",
                  "DJI Matrice 300 RTK",
                  "DJI",
                  new BigDecimal("6.30"),
                  55,
                  new BigDecimal("15.0"),
                  new BigDecimal("7000"),
                  "23",
                  "Zenmuse H20T (20 MP)",
                  List.of(
                      "RTK_POSITIONING",
                      "THERMAL_CAMERA",
                      "OBSTACLE_AVOIDANCE",
                      "FAILSAFE_RTH",
                      "WEATHERPROOF_IP")),
              List.of("matrice 300", "m300 rtk", "m300")),
          new PresetEntry(
              new DronePresetResponse(
                  "dji-m30t",
                  "DJI Matrice 30T",
                  "DJI",
                  new BigDecimal("3.77"),
                  41,
                  new BigDecimal("12.0"),
                  new BigDecimal("7000"),
                  "16",
                  "48 MP Zoom + Wide + Thermal",
                  List.of(
                      "RTK_POSITIONING",
                      "THERMAL_CAMERA",
                      "OBSTACLE_AVOIDANCE",
                      "FAILSAFE_RTH",
                      "WEATHERPROOF_IP")),
              List.of("matrice 30t", "matrice 30", "m30t")),
          new PresetEntry(
              new DronePresetResponse(
                  "dji-mavic-3e",
                  "DJI Mavic 3 Enterprise (M3E)",
                  "DJI",
                  new BigDecimal("0.915"),
                  45,
                  new BigDecimal("12.0"),
                  new BigDecimal("6000"),
                  "56",
                  "20 MP 4/3 CMOS + Tele",
                  List.of("RTK_POSITIONING", "OBSTACLE_AVOIDANCE", "FAILSAFE_RTH")),
              List.of("mavic 3 enterprise", "mavic 3e", "m3e")),
          new PresetEntry(
              new DronePresetResponse(
                  "dji-mavic-3t",
                  "DJI Mavic 3 Thermal (M3T)",
                  "DJI",
                  new BigDecimal("0.920"),
                  45,
                  new BigDecimal("12.0"),
                  new BigDecimal("6000"),
                  "56",
                  "48 MP Wide + Thermal",
                  List.of(
                      "RTK_POSITIONING", "THERMAL_CAMERA", "OBSTACLE_AVOIDANCE", "FAILSAFE_RTH")),
              List.of("mavic 3 thermal", "mavic 3t", "m3t")),
          new PresetEntry(
              new DronePresetResponse(
                  "dji-mini-4-pro",
                  "DJI Mini 4 Pro",
                  "DJI",
                  new BigDecimal("0.249"),
                  34,
                  new BigDecimal("10.7"),
                  new BigDecimal("4000"),
                  "1",
                  "48 MP 1/1.3-inch CMOS",
                  List.of("OBSTACLE_AVOIDANCE", "FAILSAFE_RTH")),
              List.of("mini 4 pro", "mini 4")),
          new PresetEntry(
              new DronePresetResponse(
                  "autel-evo-max-4t",
                  "Autel EVO Max 4T",
                  "Autel Robotics",
                  new BigDecimal("1.60"),
                  42,
                  new BigDecimal("12.0"),
                  new BigDecimal("4000"),
                  "10",
                  "50 MP Wide + 48 MP Zoom + Thermal",
                  List.of(
                      "THERMAL_CAMERA",
                      "OBSTACLE_AVOIDANCE",
                      "FAILSAFE_RTH",
                      "WEATHERPROOF_IP",
                      "GNSS_DENIED_POSITIONING")),
              List.of("evo max 4t", "autel evo max")),
          new PresetEntry(
              new DronePresetResponse(
                  "wingtra-one-gen2",
                  "WingtraOne GEN II",
                  "Wingtra",
                  new BigDecimal("4.50"),
                  59,
                  new BigDecimal("12.0"),
                  new BigDecimal("5000"),
                  "1",
                  "Sony RX1R II 42 MP",
                  List.of("RTK_POSITIONING", "FAILSAFE_RTH")),
              List.of("wingtraone", "wingtra")));

  private DroneSpecCatalog() {}

  public static List<DronePresetResponse> list() {
    return PRESETS.stream().map(PresetEntry::preset).toList();
  }

  public static Optional<DronePresetResponse> findBestMatch(String... hints) {
    for (String hint : hints) {
      if (hint == null || hint.isBlank()) continue;
      String normalized = hint.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
      for (PresetEntry entry : PRESETS) {
        for (String keyword : entry.keywords()) {
          if (normalized.contains(keyword)) {
            return Optional.of(entry.preset());
          }
        }
      }
    }
    return Optional.empty();
  }

  private record PresetEntry(DronePresetResponse preset, List<String> keywords) {
    PresetEntry(DronePresetResponse preset) {
      this(preset, List.of(preset.model().toLowerCase(Locale.ROOT)));
    }
  }
}
