package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.DronePresetResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneSpecExtractionResponse;
import com.smartdroneinspection.shared.api.DroneSpecVisionPort;
import com.smartdroneinspection.shared.api.DroneSpecVisionPort.ExtractedSpecs;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator.ValidatedUpload;
import com.smartdroneinspection.users.UserAccess;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates advisory airframe spec and registration field extraction from an uploaded image or
 * PDF using PDF text parsing, optional Vision LLM extraction, and manufacturer presets.
 */
@Service
public class DroneSpecExtractionService {

  private static final Logger log = LoggerFactory.getLogger(DroneSpecExtractionService.class);

  private static final Pattern SERIAL_PATTERN =
      Pattern.compile(
          "(?:serial(?:\\s*(?:no|number|#|s/n))?|s/n|số\\s*serial)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9\\-]{4,39})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern REG_NO_PATTERN =
      Pattern.compile(
          "(?:registration(?:\\s*(?:no|number|#))?|số\\s*(?:đăng\\s*ký|dk|hiệu))\\s*[:\\-]?\\s*([A-Z0-9Đđ][A-Z0-9\\-/]{2,31})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern EXPIRY_PATTERN =
      Pattern.compile(
          "(?:expir(?:y|es|ation)(?:\\s*date)?|valid\\s*(?:until|to)|ngày\\s*hết\\s*hạn|hiệu\\s*lực\\s*đến)\\s*[:\\-]?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern MODEL_PATTERN =
      Pattern.compile(
          "(?:(?:drone|aircraft|uav)\\s*model|model|tên\\s*(?:drone|thiết\\s*bị)|kiểu\\s*loại)\\s*[:\\-]\\s*([^\\r\\n,;]{2,80})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern MANUFACTURER_PATTERN =
      Pattern.compile(
          "(?:manufacturer|hãng\\s*(?:sản\\s*xuất|sx)|nhà\\s*sản\\s*xuất)\\s*[:\\-]\\s*([^\\r\\n,;]{2,60})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern MTOW_PATTERN =
      Pattern.compile(
          "(?:mtow|max(?:imum)?\\s*take\\s*-?off\\s*weight(?:\\s*\\([^)]+\\))?|trọng\\s*lượng\\s*cất\\s*cánh)\\s*[:\\-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(kg|g\\b)?",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern FLIGHT_TIME_PATTERN =
      Pattern.compile(
          "(?:max(?:imum)?\\s*flight\\s*time|thời\\s*gian\\s*bay(?:\\s*tối\\s*đa)?)\\s*[:\\-]?\\s*(\\d{1,4})\\s*(?:min|mins|minutes|phút)?",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern WIND_PATTERN =
      Pattern.compile(
          "(?:max(?:imum)?\\s*wind\\s*resistance|kháng\\s*gió(?:\\s*tối\\s*đa)?)\\s*[:\\-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:m/s|mps)?",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern ALTITUDE_PATTERN =
      Pattern.compile(
          "(?:max(?:imum)?\\s*(?:service\\s*)?(?:ceiling|altitude)|trần\\s*bay(?:\\s*tối\\s*đa)?)\\s*[:\\-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*m?",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern ZOOM_PATTERN =
      Pattern.compile(
          "(?:optical\\s*zoom|hybrid\\s*zoom|zoom\\s*quang\\s*học)\\s*[:\\-]?\\s*(\\d{1,3})\\s*x?",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern CAMERA_PATTERN =
      Pattern.compile(
          "(?:camera(?:\\s*(?:sensor|resolution))?|cảm\\s*biến|độ\\s*phân\\s*giải\\s*camera)\\s*[:\\-]\\s*([^\\r\\n]{2,60})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private final UserAccess userAccess;
  private final Optional<DroneSpecVisionPort> visionPort;

  public DroneSpecExtractionService(
      UserAccess userAccess, Optional<DroneSpecVisionPort> visionPort) {
    this.userAccess = userAccess;
    this.visionPort = visionPort;
  }

  public List<DronePresetResponse> listPresets(UUID inspectorId) {
    requireInspector(inspectorId);
    return DroneSpecCatalog.list();
  }

  public DroneSpecExtractionResponse extract(UUID inspectorId, MultipartFile file) {
    return extract(inspectorId, file, null);
  }

  public DroneSpecExtractionResponse extract(
      UUID inspectorId, MultipartFile file, String clientOcrText) {
    requireInspector(inspectorId);
    ValidatedUpload upload = EvidenceUploadValidator.validate(file);
    boolean isPdf = ".pdf".equals(upload.extension());
    List<String> notices = new ArrayList<>();

    String pdfText = "";
    byte[] visionImage = upload.content();
    String visionContentType = upload.contentType();
    if (isPdf) {
      PdfInspection pdf = inspectPdf(upload.content(), visionPort.isPresent());
      pdfText = pdf.text();
      if (pdf.pageOnePng() != null) {
        visionImage = pdf.pageOnePng();
        visionContentType = "image/png";
      } else if (visionPort.isPresent()) {
        notices.add("Could not render PDF page for visual inspection; using embedded PDF text.");
      }
    }

    String filename = file != null ? file.getOriginalFilename() : null;
    String documentText =
        !pdfText.isBlank() ? pdfText : (clientOcrText == null ? "" : clientOcrText.trim());
    ExtractedSpecs textExtracted = parseTextSpecs(documentText);

    ExtractedSpecs visionExtracted = null;
    boolean visionSucceeded = false;
    if (visionPort.isPresent() && !"application/pdf".equals(visionContentType)) {
      try {
        visionExtracted = visionPort.get().extract(visionImage, visionContentType, documentText);
        visionSucceeded = true;
      } catch (IOException ex) {
        log.warn("Vision drone spec extraction degraded gracefully: {}", ex.getMessage());
        notices.add(
            "Vision AI extraction unavailable right now; fell back to text & catalog matching.");
      }
    }

    String model =
        firstNonBlank(
            visionExtracted == null ? null : visionExtracted.model(), textExtracted.model());
    Optional<DronePresetResponse> preset =
        DroneSpecCatalog.findBestMatch(model, documentText, filename);

    String finalModel = firstNonBlank(model, preset.map(DronePresetResponse::model).orElse(null));
    String manufacturer =
        firstNonBlank(
            visionExtracted == null ? null : visionExtracted.manufacturer(),
            textExtracted.manufacturer(),
            preset.map(DronePresetResponse::manufacturer).orElse(null));
    String serialNumber =
        firstNonBlank(
            visionExtracted == null ? null : visionExtracted.serialNumber(),
            textExtracted.serialNumber());
    String registrationNumber =
        firstNonBlank(
            visionExtracted == null ? null : visionExtracted.registrationNumber(),
            textExtracted.registrationNumber());
    LocalDate expiry =
        visionExtracted != null && visionExtracted.registrationExpiresAt() != null
            ? visionExtracted.registrationExpiresAt()
            : textExtracted.registrationExpiresAt();
    BigDecimal mtowKg =
        firstPositive(
            visionExtracted == null ? null : visionExtracted.mtowKg(),
            textExtracted.mtowKg(),
            preset.map(DronePresetResponse::mtowKg).orElse(null));
    Integer maxFlightTime =
        firstPositiveInt(
            visionExtracted == null ? null : visionExtracted.maxFlightTimeMinutes(),
            textExtracted.maxFlightTimeMinutes(),
            preset.map(DronePresetResponse::maxFlightTimeMinutes).orElse(null));
    BigDecimal windResistance =
        firstPositive(
            visionExtracted == null ? null : visionExtracted.maxWindResistanceMps(),
            textExtracted.maxWindResistanceMps(),
            preset.map(DronePresetResponse::maxWindResistanceMps).orElse(null));
    BigDecimal maxAltitude =
        firstPositive(
            visionExtracted == null ? null : visionExtracted.maxAltitudeMeters(),
            textExtracted.maxAltitudeMeters(),
            preset.map(DronePresetResponse::maxAltitudeMeters).orElse(null));
    String opticalZoom =
        firstNonBlank(
            visionExtracted == null ? null : visionExtracted.opticalZoomLevel(),
            textExtracted.opticalZoomLevel(),
            preset.map(DronePresetResponse::opticalZoomLevel).orElse(null));
    String cameraSensor =
        firstNonBlank(
            visionExtracted == null ? null : visionExtracted.cameraSensor(),
            textExtracted.cameraSensor(),
            preset.map(DronePresetResponse::cameraSensor).orElse(null));

    Set<String> mergedFeatures = new LinkedHashSet<>();
    if (visionExtracted != null && visionExtracted.features() != null) {
      mergedFeatures.addAll(visionExtracted.features());
    }
    if (textExtracted.features() != null) {
      mergedFeatures.addAll(textExtracted.features());
    }
    preset.ifPresent(p -> mergedFeatures.addAll(p.features()));

    List<String> suggestedFields = new ArrayList<>();
    if (finalModel != null) suggestedFields.add("droneName");
    if (manufacturer != null) suggestedFields.add("manufacturer");
    if (serialNumber != null) suggestedFields.add("serialNumber");
    if (registrationNumber != null) suggestedFields.add("registrationNumber");
    if (expiry != null) suggestedFields.add("registrationExpiresAt");
    if (mtowKg != null) suggestedFields.add("mtowKg");
    if (maxFlightTime != null) suggestedFields.add("maxFlightTimeMinutes");
    if (windResistance != null) suggestedFields.add("maxWindResistanceMps");
    if (maxAltitude != null) suggestedFields.add("maxAltitudeMeters");
    if (opticalZoom != null) suggestedFields.add("opticalZoomLevel");
    if (cameraSensor != null) suggestedFields.add("cameraSensor");
    if (!mergedFeatures.isEmpty()) suggestedFields.add("features");

    if (visionPort.isEmpty() && !isPdf && suggestedFields.isEmpty()) {
      notices.add(
          "Could not read clear text from this image automatically. Enable Vision AI (`COMPATIBLE_VISION_ENABLED=true`) or upload a clearer photo / PDF.");
    }

    String source =
        resolveSource(
            visionSucceeded,
            isPdf,
            !documentText.isBlank(),
            preset.isPresent(),
            suggestedFields.isEmpty());

    return new DroneSpecExtractionResponse(
        finalModel,
        manufacturer,
        serialNumber,
        registrationNumber,
        expiry,
        mtowKg,
        maxFlightTime,
        windResistance,
        maxAltitude,
        opticalZoom,
        cameraSensor,
        List.copyOf(mergedFeatures),
        visionPort.isPresent(),
        source,
        preset.map(DronePresetResponse::model).orElse(null),
        List.copyOf(suggestedFields),
        List.copyOf(notices));
  }

  static ExtractedSpecs parseTextSpecs(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      return new ExtractedSpecs(
          null, null, null, null, null, null, null, null, null, null, null, List.of());
    }
    String model = captureGroup(MODEL_PATTERN, rawText);
    String manufacturer = captureGroup(MANUFACTURER_PATTERN, rawText);
    String serial = captureGroup(SERIAL_PATTERN, rawText);
    String regNo = captureGroup(REG_NO_PATTERN, rawText);
    LocalDate expiry = parseDate(captureGroup(EXPIRY_PATTERN, rawText));
    BigDecimal mtow = parseMtow(rawText);
    Integer flightTime = parseInteger(captureGroup(FLIGHT_TIME_PATTERN, rawText));
    BigDecimal wind = parseDecimal(captureGroup(WIND_PATTERN, rawText));
    BigDecimal altitude = parseDecimal(captureGroup(ALTITUDE_PATTERN, rawText));
    String zoom = captureGroup(ZOOM_PATTERN, rawText);
    String camera = captureGroup(CAMERA_PATTERN, rawText);

    List<String> features = new ArrayList<>();
    String lower = rawText.toLowerCase(Locale.ROOT);
    if (lower.contains("rtk")) features.add("RTK_POSITIONING");
    if (lower.contains("thermal") || lower.contains("camera nhiệt")) features.add("THERMAL_CAMERA");
    if (lower.contains("lidar")) features.add("LIDAR_SCANNER");
    if (lower.contains("night vision")) features.add("NIGHT_VISION");
    if (lower.contains("obstacle") || lower.contains("tránh va chạm")) {
      features.add("OBSTACLE_AVOIDANCE");
    }
    if (lower.contains("rth")
        || lower.contains("return-to-home")
        || lower.contains("return to home")) {
      features.add("FAILSAFE_RTH");
    }
    if (lower.contains("ip54")
        || lower.contains("ip55")
        || lower.contains("ip45")
        || lower.contains("weatherproof")) {
      features.add("WEATHERPROOF_IP");
    }

    return new ExtractedSpecs(
        model,
        manufacturer,
        serial,
        regNo,
        expiry,
        mtow,
        flightTime,
        wind,
        altitude,
        zoom,
        camera,
        List.copyOf(features));
  }

  private PdfInspection inspectPdf(byte[] pdfBytes, boolean renderPageForVision) {
    try (PDDocument doc = PDDocument.load(pdfBytes)) {
      String text = doc.getNumberOfPages() > 0 ? new PDFTextStripper().getText(doc) : "";
      byte[] png = null;
      if (renderPageForVision && doc.getNumberOfPages() > 0) {
        BufferedImage image = new PDFRenderer(doc).renderImageWithDPI(0, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        png = out.toByteArray();
      }
      return new PdfInspection(text == null ? "" : text, png);
    } catch (IOException | RuntimeException ex) {
      return new PdfInspection("", null);
    }
  }

  private void requireInspector(UUID inspectorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(inspectorId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Active inspector account is required"));
    if (!actor.hasRole("INSPECTOR")) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an Inspector can extract drone specifications");
    }
  }

  private static String resolveSource(
      boolean visionOk, boolean isPdf, boolean hasText, boolean hasPreset, boolean emptyResult) {
    if (emptyResult) return "NONE";
    if (visionOk && hasPreset) return "VISION_AND_PRESET";
    if (visionOk) return "VISION_AI";
    if (isPdf && hasText && hasPreset) return "PDF_TEXT_AND_PRESET";
    if (isPdf && hasText) return "PDF_TEXT";
    if (hasText && hasPreset) return "OCR_TEXT_AND_PRESET";
    if (hasText) return "OCR_TEXT";
    return "FILENAME_PRESET";
  }

  private static String captureGroup(Pattern pattern, String input) {
    Matcher m = pattern.matcher(input);
    return m.find() ? m.group(1).trim() : null;
  }

  private static BigDecimal parseMtow(String rawText) {
    Matcher m = MTOW_PATTERN.matcher(rawText);
    if (!m.find()) return null;
    BigDecimal val = parseDecimal(m.group(1));
    if (val == null) return null;
    String unit = m.group(2);
    if (unit != null && unit.equalsIgnoreCase("g")) {
      return val.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }
    return val;
  }

  private static BigDecimal parseDecimal(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      BigDecimal val = new BigDecimal(raw.trim().replace(',', '.'));
      return val.signum() > 0 ? val : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Integer parseInteger(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      int val = Integer.parseInt(raw.trim());
      return val > 0 ? val : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    for (DateTimeFormatter fmt :
        List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"))) {
      try {
        return LocalDate.parse(raw.trim(), fmt);
      } catch (DateTimeParseException ignored) {
        // try next
      }
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.trim();
    }
    return null;
  }

  private static BigDecimal firstPositive(BigDecimal... values) {
    for (BigDecimal v : values) {
      if (v != null && v.signum() > 0) return v;
    }
    return null;
  }

  private static Integer firstPositiveInt(Integer... values) {
    for (Integer v : values) {
      if (v != null && v > 0) return v;
    }
    return null;
  }

  private record PdfInspection(String text, byte[] pageOnePng) {}
}
