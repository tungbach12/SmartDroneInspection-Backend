package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.api.dto.request.CreateInspectorFlightLogRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateInspectorProfileRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorFlightLogResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.domain.InspectorFlightLog;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.repository.InspectorFlightLogRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class InspectorProfileService {

  private final UserRepository userRepository;
  private final InspectorFlightLogRepository flightLogRepository;
  private final InspectorPartnerLicenseDocumentService licenseDocumentService;

  public InspectorProfileService(
      UserRepository userRepository,
      InspectorFlightLogRepository flightLogRepository,
      InspectorPartnerLicenseDocumentService licenseDocumentService) {
    this.userRepository = userRepository;
    this.flightLogRepository = flightLogRepository;
    this.licenseDocumentService = licenseDocumentService;
  }

  @Transactional(readOnly = true)
  public List<InspectorProfileResponse> listInspectors() {
    List<User> inspectors = userRepository.findAllInspectors();
    return inspectors.stream().map(this::toProfileResponse).toList();
  }

  @Transactional(readOnly = true)
  public InspectorProfileResponse getProfile(UUID inspectorUserId) {
    User user = findInspector(inspectorUserId);
    return toProfileResponse(user);
  }

  public InspectorProfileResponse updateProfile(
      UUID inspectorUserId, UpdateInspectorProfileRequest request, UUID actorId) {
    User user = findInspector(inspectorUserId);
    if (request.fullName() != null && !request.fullName().isBlank()) {
      user.setFullName(request.fullName().trim());
    }
    user.setPhoneNumber(request.phoneNumber() != null ? request.phoneNumber().trim() : null);
    user.setCitizenId(request.citizenId() != null ? request.citizenId().trim() : null);
    user.setDateOfBirth(request.dateOfBirth());
    user.setAddress(request.address() != null ? request.address().trim() : null);
    user.setNotes(request.notes() != null ? request.notes().trim() : null);

    // Update Pilot license
    user.updatePilotLicense(
        request.pilotLicenseNumber() != null ? request.pilotLicenseNumber().trim() : null,
        request.pilotLicenseClass() != null ? request.pilotLicenseClass().trim() : null,
        request.pilotLicenseIssuedDate(),
        request.pilotLicenseExpiresAt(),
        request.pilotLicenseAuthority() != null ? request.pilotLicenseAuthority().trim() : null,
        null);

    // Update Flight experience
    if (request.priorFlightHours() != null) {
      user.setPriorFlightHours(request.priorFlightHours());
    }
    user.setCertifiedDroneModels(
        request.certifiedDroneModels() != null ? request.certifiedDroneModels().trim() : null);
    user.setSpecializations(
        request.specializations() != null ? request.specializations().trim() : null);
    user.setExperienceSummary(
        request.experienceSummary() != null ? request.experienceSummary().trim() : null);

    User saved = userRepository.save(user);
    return toProfileResponse(saved);
  }

  public InspectorProfileResponse uploadLicenseScan(
      UUID inspectorUserId, MultipartFile file, UUID actorId) {
    User user = findInspector(inspectorUserId);
    var uploadResponse = licenseDocumentService.upload(file);
    user.setPilotLicenseFileKey(uploadResponse.fileKey());
    User saved = userRepository.save(user);
    return toProfileResponse(saved);
  }

  @Transactional(readOnly = true)
  public InspectorPartnerLicenseDocumentService.InputStreamContent openLicenseScan(
      UUID inspectorUserId) {
    User user = findInspector(inspectorUserId);
    String fileKey = user.getPilotLicenseFileKey();
    if (fileKey == null || fileKey.isBlank()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "NOT_FOUND", "Bản scan bằng lái của inspector chưa được tải lên");
    }
    return licenseDocumentService.open(fileKey);
  }

  @Transactional(readOnly = true)
  public List<InspectorFlightLogResponse> listFlightLogs(UUID inspectorUserId) {
    findInspector(inspectorUserId);
    return flightLogRepository.findByInspectorUserIdOrderByFlightDateDesc(inspectorUserId).stream()
        .map(this::toFlightLogResponse)
        .toList();
  }

  public InspectorFlightLogResponse recordFlightLog(
      UUID inspectorUserId, CreateInspectorFlightLogRequest request, UUID actorId) {
    findInspector(inspectorUserId);
    InspectorFlightLog log =
        new InspectorFlightLog(
            inspectorUserId,
            request.flightCode().trim(),
            request.flightDate(),
            request.missionTitle().trim(),
            request.droneModel().trim(),
            request.droneSerialNumber() != null ? request.droneSerialNumber().trim() : null,
            request.location().trim(),
            request.flightDurationMinutes(),
            request.inspectionType().trim(),
            request.safetyIncident(),
            request.notes() != null ? request.notes().trim() : null,
            actorId);
    InspectorFlightLog saved = flightLogRepository.save(log);
    return toFlightLogResponse(saved);
  }

  public void deleteFlightLog(UUID inspectorUserId, UUID logId, UUID actorId) {
    InspectorFlightLog log =
        flightLogRepository
            .findById(logId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Chuyến bay không tồn tại"));
    if (!log.getInspectorUserId().equals(inspectorUserId)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_FLIGHT_LOG", "Chuyến bay không thuộc về inspector này");
    }
    flightLogRepository.delete(log);
  }

  public void recordAutomaticFlightSession(
      UUID inspectorUserId,
      String flightCode,
      java.time.LocalDate flightDate,
      String missionTitle,
      String droneModel,
      String droneSerialNumber,
      String location,
      int durationMinutes,
      String inspectionType,
      String outcomeNotes) {
    if (inspectorUserId == null) {
      return;
    }
    if (flightCode != null && flightLogRepository.existsByFlightCode(flightCode)) {
      return;
    }

    java.time.LocalDate resolvedDate = flightDate != null ? flightDate : java.time.LocalDate.now();
    int resolvedDuration = Math.max(1, durationMinutes);

    InspectorFlightLog log =
        new InspectorFlightLog(
            inspectorUserId,
            flightCode != null
                ? flightCode
                : "FL-AUTO-" + UUID.randomUUID().toString().substring(0, 8),
            resolvedDate,
            missionTitle != null ? missionTitle : "Kiểm tra thực địa",
            droneModel != null ? droneModel : "UAV",
            droneSerialNumber,
            location != null ? location : "Hiện trường",
            resolvedDuration,
            inspectionType != null ? inspectionType : "Kiểm tra định kỳ",
            false,
            outcomeNotes,
            null);
    flightLogRepository.save(log);

    userRepository
        .findById(inspectorUserId)
        .ifPresent(
            user -> {
              boolean updated = false;
              if (droneModel != null
                  && !droneModel.isBlank()
                  && !"UAV".equalsIgnoreCase(droneModel)) {
                String current = user.getCertifiedDroneModels();
                if (current == null || current.isBlank()) {
                  user.setCertifiedDroneModels(droneModel.trim());
                  updated = true;
                } else if (java.util.Arrays.stream(current.split(","))
                    .map(String::trim)
                    .noneMatch(m -> m.equalsIgnoreCase(droneModel.trim()))) {
                  user.setCertifiedDroneModels(current.trim() + ", " + droneModel.trim());
                  updated = true;
                }
              }

              if (inspectionType != null
                  && !inspectionType.isBlank()
                  && !"Kiểm tra thực địa".equalsIgnoreCase(inspectionType)) {
                String current = user.getSpecializations();
                if (current == null || current.isBlank()) {
                  user.setSpecializations(inspectionType.trim());
                  updated = true;
                } else if (java.util.Arrays.stream(current.split(","))
                    .map(String::trim)
                    .noneMatch(s -> s.equalsIgnoreCase(inspectionType.trim()))) {
                  user.setSpecializations(current.trim() + ", " + inspectionType.trim());
                  updated = true;
                }
              }

              if (updated) {
                userRepository.save(user);
              }
            });
  }

  private User findInspector(UUID inspectorUserId) {
    return userRepository
        .findById(inspectorUserId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Tài khoản Inspector không tồn tại"));
  }

  private InspectorProfileResponse toProfileResponse(User user) {
    List<InspectorFlightLog> logs =
        flightLogRepository.findByInspectorUserIdOrderByFlightDateDesc(user.getId());
    int totalMinutes = logs.stream().mapToInt(InspectorFlightLog::getFlightDurationMinutes).sum();
    BigDecimal companyFlightHours =
        BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    BigDecimal priorHours = user.getPriorFlightHours();
    BigDecimal totalFlightHours = priorHours.add(companyFlightHours);

    String expirationStatus = "UNKNOWN";
    Long remainingDays = null;
    if (user.getPilotLicenseExpiresAt() != null) {
      Instant exp = user.getPilotLicenseExpiresAt();
      Instant now = Instant.now();
      long days = ChronoUnit.DAYS.between(now, exp);
      remainingDays = days;
      if (exp.isBefore(now)) {
        expirationStatus = "EXPIRED";
      } else if (days <= 60) {
        expirationStatus = "EXPIRING_SOON";
      } else {
        expirationStatus = "VALID";
      }
    }

    boolean hasLicenseScan =
        user.getPilotLicenseFileKey() != null && !user.getPilotLicenseFileKey().isBlank();

    return new InspectorProfileResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhoneNumber(),
        user.getAvatarUrl(),
        user.getStatus().name(),
        user.getActorZone().name(),
        user.getCitizenId(),
        user.getDateOfBirth(),
        user.getAddress(),
        user.getNotes(),
        user.getPilotLicenseNumber(),
        user.getPilotLicenseClass(),
        user.getPilotLicenseIssuedDate(),
        user.getPilotLicenseExpiresAt(),
        user.getPilotLicenseAuthority(),
        user.getPilotLicenseFileKey(),
        hasLicenseScan,
        expirationStatus,
        remainingDays,
        priorHours,
        companyFlightHours,
        totalFlightHours,
        logs.size(),
        user.getCertifiedDroneModels(),
        user.getSpecializations(),
        user.getExperienceSummary(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private InspectorFlightLogResponse toFlightLogResponse(InspectorFlightLog log) {
    double hours =
        BigDecimal.valueOf(log.getFlightDurationMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
            .doubleValue();
    return new InspectorFlightLogResponse(
        log.getId(),
        log.getInspectorUserId(),
        log.getFlightCode(),
        log.getFlightDate(),
        log.getMissionTitle(),
        log.getDroneModel(),
        log.getDroneSerialNumber(),
        log.getLocation(),
        log.getFlightDurationMinutes(),
        hours,
        log.getInspectionType(),
        log.isSafetyIncident(),
        log.getNotes(),
        log.getRecordedByUserId(),
        log.getCreatedAt());
  }
}
