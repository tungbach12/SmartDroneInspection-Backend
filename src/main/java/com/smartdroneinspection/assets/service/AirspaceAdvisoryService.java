package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Advisory airspace and no-fly zone evaluation aligned with Decision 18/2020/QĐ-TTg, Law on
 * People's Air Defence 2024, Decree 288/2025/NĐ-CP, and {@code cambay.mod.gov.vn}.
 *
 * <p>Informational airspace lookups assist mission planning and surface {@code MSG28} warnings when
 * an asset or planned flight falls inside a prohibited or restricted zone; they never substitute
 * for an official aviation flight permit.
 */
@Service
public class AirspaceAdvisoryService {

  public static final String PORTAL_URL = "https://cambay.mod.gov.vn";
  public static final String MSG28_CODE = "MSG28";
  public static final String CLEAR_CODE = "AIRSPACE_CLEAR";
  public static final String MSG28_TEXT =
      "A possible restriction is indicated. Verify current applicable authority permission; this lookup is not a flight permit.";
  public static final String CLEAR_TEXT =
      "Coordinates are outside published static no-fly and restricted zones. Verify current NOTAM and authority permission before flight; this lookup is not a flight permit.";

  private static final double EARTH_RADIUS_METERS = 6_371_000.0;
  private static final long NEARBY_BUFFER_METERS = 5_000L;

  private static final List<AirspaceZoneResponse> PUBLISHED_ZONES =
      List.of(
          new AirspaceZoneResponse(
              "VN-NFZ-SGN-TSN-CORE",
              "Cảng HKQT Tân Sơn Nhất — Vùng Cấm Bay Tuyệt Đối",
              "TP. Hồ Chí Minh",
              "NO_FLY_ZONE",
              BigDecimal.valueOf(10.8188),
              BigDecimal.valueOf(106.6519),
              3500,
              0,
              "Cục Tác chiến - Bộ Tổng Tham mưu (Bộ Quốc phòng)",
              "Quyết định 18/2020/QĐ-TTg & Nghị định 288/2025/NĐ-CP",
              "Khu vực đường băng và vùng phụ cận trực tiếp sân bay quốc tế Tân Sơn Nhất.",
              null),
          new AirspaceZoneResponse(
              "VN-RES-SGN-TSN-CTR",
              "Vùng Kiểm Soát Tiếp Cận Tân Sơn Nhất (CTR)",
              "TP. Hồ Chí Minh",
              "RESTRICTED_ZONE",
              BigDecimal.valueOf(10.8188),
              BigDecimal.valueOf(106.6519),
              8000,
              50,
              "Cục Tác chiến & Sư đoàn Không quân 370",
              "Quyết định 18/2020/QĐ-TTg & Luật Phòng không nhân dân 2024",
              "Hành lang tiếp cận hạ cánh sân bay Tân Sơn Nhất; bắt buộc có giấy phép hiệp đồng tác chiến.",
              null),
          new AirspaceZoneResponse(
              "VN-NFZ-SGN-GOV-CORE",
              "Khu Trung Tâm Hành Chính - Dinh Độc Lập & Quân Khu 7",
              "TP. Hồ Chí Minh",
              "NO_FLY_ZONE",
              BigDecimal.valueOf(10.7769),
              BigDecimal.valueOf(106.6953),
              1100,
              0,
              "Bộ Tư lệnh TP. Hồ Chí Minh",
              "Quyết định 18/2020/QĐ-TTg & Nghị định 288/2025/NĐ-CP",
              "Khu vực cơ quan trọng yếu, Dinh Độc Lập và trụ sở quốc phòng trung tâm Quận 1.",
              null),
          new AirspaceZoneResponse(
              "VN-RES-SGN-SAIGON-RIVER",
              "Hành Lang An Toàn Hàng Không & Cầu Trọng Điểm Sông Sài Gòn",
              "TP. Hồ Chí Minh",
              "RESTRICTED_ZONE",
              BigDecimal.valueOf(10.7789),
              BigDecimal.valueOf(106.7082),
              2200,
              120,
              "Bộ Tư lệnh TP. Hồ Chí Minh",
              "Nghị định 288/2025/NĐ-CP (Khu vực hạn chế bay đô thị)",
              "Khu vực cầu Thủ Thiêm, cầu Ba Son và luồng tàu thủy sông Sài Gòn; giới hạn độ cao <= 120m AGL theo giấy phép.",
              null),
          new AirspaceZoneResponse(
              "VN-RES-SGN-CATLAI-500KV",
              "Hành Lang Bảo Vệ Lưới Điện Cao Thế 500kV & Cảng Cát Lái",
              "TP. Hồ Chí Minh",
              "RESTRICTED_ZONE",
              BigDecimal.valueOf(10.7615),
              BigDecimal.valueOf(106.7754),
              1800,
              100,
              "Bộ Tư lệnh TP.HCM & Đơn vị Quản lý Lưới điện",
              "Nghị định 288/2025/NĐ-CP & Quy định an toàn hành lang lưới điện cao áp",
              "Hành lang trạm và trụ vượt sông 500kV Cát Lái; yêu cầu giấy phép bay và phương án chống nhiễu từ trường.",
              null),
          new AirspaceZoneResponse(
              "VN-NFZ-HAN-BADINH",
              "Khu Vực Cấm Bay Đặc Biệt Ba Đình - Trung Tâm Chính Trị Quốc Gia",
              "Hà Nội",
              "NO_FLY_ZONE",
              BigDecimal.valueOf(21.0369),
              BigDecimal.valueOf(105.8342),
              2500,
              0,
              "Cục Tác chiến - Bộ Tổng Tham mưu (Bộ Quốc phòng)",
              "Quyết định 18/2020/QĐ-TTg & Luật Phòng không nhân dân 2024",
              "Khu vực Lăng Chủ tịch Hồ Chí Minh, Tòa nhà Quốc hội, Phủ Chủ tịch và trụ sở Bộ Quốc phòng.",
              null),
          new AirspaceZoneResponse(
              "VN-NFZ-HAN-NOIBAI-CORE",
              "Cảng HKQT Nội Bài — Vùng Cấm Bay Tuyệt Đối",
              "Hà Nội",
              "NO_FLY_ZONE",
              BigDecimal.valueOf(21.2187),
              BigDecimal.valueOf(105.8042),
              4000,
              0,
              "Cục Tác chiến & Sư đoàn Không quân 371",
              "Quyết định 18/2020/QĐ-TTg",
              "Khu vực đường cất hạ cánh và đài chỉ huy Cảng hàng không quốc tế Nội Bài.",
              null),
          new AirspaceZoneResponse(
              "VN-NFZ-DAD-AIRPORT-CORE",
              "Cảng HKQT Đà Nẵng & Khu Quân Sự Sư Đoàn 372",
              "Đà Nẵng",
              "NO_FLY_ZONE",
              BigDecimal.valueOf(16.0539),
              BigDecimal.valueOf(108.1994),
              3500,
              0,
              "Cục Tác chiến & Sư đoàn Không quân 372",
              "Quyết định 18/2020/QĐ-TTg & Nghị định 288/2025/NĐ-CP",
              "Khu vực sân bay hỗn hợp dân sự - quân sự Đà Nẵng.",
              null));

  public List<AirspaceZoneResponse> listZones() {
    return PUBLISHED_ZONES;
  }

  public AirspaceEvaluationResponse evaluate(BigDecimal latitude, BigDecimal longitude) {
    validateCoordinates(latitude, longitude);
    double lat = latitude.doubleValue();
    double lon = longitude.doubleValue();

    List<AirspaceZoneResponse> withDistances =
        PUBLISHED_ZONES.stream()
            .map(
                zone ->
                    zone.withDistanceMeters(
                        Math.round(
                            haversineMeters(
                                lat,
                                lon,
                                zone.latitude().doubleValue(),
                                zone.longitude().doubleValue()))))
            .sorted(
                Comparator.comparingInt(
                        (AirspaceZoneResponse z) -> "NO_FLY_ZONE".equals(z.zoneType()) ? 0 : 1)
                    .thenComparingLong(AirspaceZoneResponse::distanceMeters))
            .toList();

    List<AirspaceZoneResponse> matching =
        withDistances.stream()
            .filter(zone -> zone.distanceMeters() <= zone.radiusMeters())
            .toList();

    List<AirspaceZoneResponse> nearby =
        withDistances.stream()
            .filter(
                zone ->
                    zone.distanceMeters() > zone.radiusMeters()
                        && zone.distanceMeters() <= zone.radiusMeters() + NEARBY_BUFFER_METERS)
            .toList();

    boolean inNoFly = matching.stream().anyMatch(z -> "NO_FLY_ZONE".equals(z.zoneType()));
    boolean inRestricted = !inNoFly && !matching.isEmpty();

    String classification =
        inNoFly ? "NO_FLY_ZONE" : (inRestricted ? "RESTRICTED_ZONE" : "CLEAR_ZONE");
    String messageCode = (inNoFly || inRestricted) ? MSG28_CODE : CLEAR_CODE;
    String advisoryMessage = (inNoFly || inRestricted) ? MSG28_TEXT : CLEAR_TEXT;

    return new AirspaceEvaluationResponse(
        latitude,
        longitude,
        classification,
        messageCode,
        advisoryMessage,
        PORTAL_URL,
        matching,
        nearby);
  }

  private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
    if (latitude == null || longitude == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Both latitude and longitude are required to evaluate airspace.");
    }
    if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
        || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Latitude must be between -90 and 90.");
    }
    if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
        || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Longitude must be between -180 and 180.");
    }
  }

  private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
  }
}
