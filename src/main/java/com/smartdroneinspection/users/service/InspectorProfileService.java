package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.api.dto.request.CreateInspectorFlightLogRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateInspectorProfileRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorFlightLogResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.domain.InspectorFlightLog;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.repository.InspectorFlightLogRepository;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
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
  private final InspectorProfileRepository profileRepository;
  private final InspectorPartnerLicenseDocumentService licenseDocumentService;

  public InspectorProfileService(
      UserRepository userRepository,
      InspectorFlightLogRepository flightLogRepository,
      InspectorProfileRepository profileRepository,
      InspectorPartnerLicenseDocumentService licenseDocumentService) {
    this.userRepository = userRepository;
    this.flightLogRepository = flightLogRepository;
    this.profileRepository = profileRepository;
    this.licenseDocumentService = licenseDocumentService;
  }

  @Transactional(readOnly = true)
  public List<InspectorProfileResponse> listInspectors() {
    List<User> inspectors = userRepository.findAllInspectors();
    return inspectors.stream()
        .map(user -> toProfileResponse(user, loadProfile(user.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public InspectorProfileResponse getProfile(UUID inspectorUserId) {
    User user = findInspector(inspectorUserId);
    return toProfileResponse(user, loadProfile(inspectorUserId));
  }

  public InspectorProfileResponse updateProfile(
      UUID inspectorUserId, UpdateInspectorProfileRequest request, UUID actorId) {
    User user = findInspector(inspectorUserId);
    if (request.fullName() != null && !request.fullName().isBlank()) {
      user.setFullName(request.fullName().trim());
    }
    user.setPhoneNumber(request.phoneNumber() != null ? request.phoneNumber().trim() : null);
    InspectorProfile profile = findOrCreateProfile(inspectorUserId);
    profile.updatePersonalDetails(
        request.citizenId() != null ? request.citizenId().trim() : null,
        request.dateOfBirth(),
        request.address() != null ? request.address().trim() : null,
        request.notes() != null ? request.notes().trim() : null);

    // Update Pilot license
    profile.updatePilotLicense(
        request.pilotLicenseNumber() != null ? request.pilotLicenseNumber().trim() : null,
        request.pilotLicenseClass() != null ? request.pilotLicenseClass().trim() : null,
        request.pilotLicenseIssuedDate(),
        request.pilotLicenseExpiresAt(),
        request.pilotLicenseAuthority() != null ? request.pilotLicenseAuthority().trim() : null,
        null);

    // Update Flight experience
    profile.updateFlightExperience(
        request.priorFlightHours(),
        request.certifiedDroneModels() != null ? request.certifiedDroneModels().trim() : null,
        request.specializations() != null ? request.specializations().trim() : null,
        request.experienceSummary() != null ? request.experienceSummary().trim() : null);

    User saved = userRepository.save(user);
    profileRepository.save(profile);
    return toProfileResponse(saved, profile);
  }

  public InspectorProfileResponse uploadLicenseScan(
      UUID inspectorUserId, MultipartFile file, UUID actorId) {
    User user = findInspector(inspectorUserId);
    var uploadResponse = licenseDocumentService.upload(file);
    InspectorProfile profile = findOrCreateProfile(inspectorUserId);
    profile.setPilotLicenseFileKey(uploadResponse.fileKey());
    userRepository.save(user);
    profileRepository.save(profile);
    return toProfileResponse(user, profile);
  }

  @Transactional(readOnly = true)
  public InspectorPartnerLicenseDocumentService.InputStreamContent openLicenseScan(
      UUID inspectorUserId) {
    findInspector(inspectorUserId);
    String fileKey =
        profileRepository
            .findById(inspectorUserId)
            .map(InspectorProfile::getPilotLicenseFileKey)
            .orElse(null);
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

    InspectorProfile profile = findOrCreateProfile(inspectorUserId);
    if (droneModel != null && !droneModel.isBlank() && !"UAV".equalsIgnoreCase(droneModel)) {
      profile.addDroneModel(droneModel);
    }
    if (inspectionType != null
        && !inspectionType.isBlank()
        && !"Kiểm tra thực địa".equalsIgnoreCase(inspectionType)) {
      profile.addSpecialization(inspectionType);
    }
    profileRepository.save(profile);
  }

  private User findInspector(UUID inspectorUserId) {
    return userRepository
        .findById(inspectorUserId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Tài khoản Inspector không tồn tại"));
  }

  private InspectorProfile findOrCreateProfile(UUID userId) {
    return profileRepository
        .findById(userId)
        .orElseGet(() -> profileRepository.save(new InspectorProfile(userId)));
  }

  private InspectorProfile loadProfile(UUID userId) {
    return profileRepository.findById(userId).orElseGet(() -> new InspectorProfile(userId));
  }

  private InspectorProfileResponse toProfileResponse(User user, InspectorProfile profile) {
    List<InspectorFlightLog> logs =
        flightLogRepository.findByInspectorUserIdOrderByFlightDateDesc(user.getId());
    int totalMinutes = logs.stream().mapToInt(InspectorFlightLog::getFlightDurationMinutes).sum();
    BigDecimal companyFlightHours =
        BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    BigDecimal priorHours = profile.getPriorFlightHours();
    BigDecimal totalFlightHours = priorHours.add(companyFlightHours);

    String expirationStatus = "UNKNOWN";
    Long remainingDays = null;
    if (profile.getPilotLicenseExpiresAt() != null) {
      Instant exp = profile.getPilotLicenseExpiresAt();
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
        profile.getPilotLicenseFileKey() != null && !profile.getPilotLicenseFileKey().isBlank();

    return new InspectorProfileResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhoneNumber(),
        user.getAvatarUrl(),
        user.getStatus().name(),
        user.getActorZone().name(),
        profile.getCitizenId(),
        profile.getDateOfBirth(),
        profile.getAddress(),
        profile.getNotes(),
        profile.getPilotLicenseNumber(),
        profile.getPilotLicenseClass(),
        profile.getPilotLicenseIssuedDate(),
        profile.getPilotLicenseExpiresAt(),
        profile.getPilotLicenseAuthority(),
        profile.getPilotLicenseFileKey(),
        hasLicenseScan,
        expirationStatus,
        remainingDays,
        priorHours,
        companyFlightHours,
        totalFlightHours,
        logs.size(),
        profile.getCertifiedDroneModels(),
        profile.getSpecializations(),
        profile.getExperienceSummary(),
        user.getCreatedAt(),
        profile.getUpdatedAt());
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
