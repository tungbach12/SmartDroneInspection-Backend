package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetDocument;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.request.UpsertInspectorRatingRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionRatingBundleResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectorRatingProfileResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectorRatingResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectorRatingSummaryResponse;
import com.smartdroneinspection.inspections.api.dto.response.RatingCriterionConfigResponse;
import com.smartdroneinspection.inspections.api.dto.response.RatingCriterionDefinitionResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectorRating;
import com.smartdroneinspection.inspections.domain.RatingCriterionConfig;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.inspections.events.InspectorRatedEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.InspectorRatingRepository;
import com.smartdroneinspection.inspections.repository.RatingCriterionConfigRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionRatingService {

  private final InspectorRatingRepository ratings;
  private final InspectionRepository inspections;
  private final AssetRepository assets;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final RatingCriterionConfigRepository criteriaConfigs;

  public InspectionRatingService(
      InspectorRatingRepository ratings,
      InspectionRepository inspections,
      AssetRepository assets,
      UserAccess userAccess,
      ApplicationEventPublisher eventPublisher) {
    this(ratings, inspections, assets, userAccess, eventPublisher, Clock.systemUTC(), null);
  }

  public InspectionRatingService(
      InspectorRatingRepository ratings,
      InspectionRepository inspections,
      AssetRepository assets,
      UserAccess userAccess,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this(ratings, inspections, assets, userAccess, eventPublisher, clock, null);
  }

  @Autowired
  public InspectionRatingService(
      InspectorRatingRepository ratings,
      InspectionRepository inspections,
      AssetRepository assets,
      UserAccess userAccess,
      ApplicationEventPublisher eventPublisher,
      RatingCriterionConfigRepository criteriaConfigs) {
    this(
        ratings,
        inspections,
        assets,
        userAccess,
        eventPublisher,
        Clock.systemUTC(),
        criteriaConfigs);
  }

  public InspectionRatingService(
      InspectorRatingRepository ratings,
      InspectionRepository inspections,
      AssetRepository assets,
      UserAccess userAccess,
      ApplicationEventPublisher eventPublisher,
      Clock clock,
      RatingCriterionConfigRepository criteriaConfigs) {
    this.ratings = ratings;
    this.inspections = inspections;
    this.assets = assets;
    this.userAccess = userAccess;
    this.eventPublisher = eventPublisher;
    this.clock = clock != null ? clock : Clock.systemUTC();
    this.criteriaConfigs = criteriaConfigs;
  }

  @Transactional
  public InspectionRatingBundleResponse upsertForInspection(
      UUID actorId, UUID inspectionId, UpsertInspectorRatingRequest request) {
    if (request == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_RATING_PAYLOAD", "Rating request body is required.");
    }
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    Inspection inspection = findAccessibleInspection(actor, inspectionId, true);
    RaterRole raterRole = resolveWritableRaterRole(actor, inspection);

    if (inspection.getStatus() != InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_NOT_COMPLETED",
          "Inspector ratings open only after the inspection report is published and completed.");
    }
    if (inspection.getInspectorId() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTOR_NOT_ASSIGNED",
          "This inspection has no assigned inspector to rate.");
    }

    Instant now = Instant.now(clock);
    InspectorRating existing =
        ratings.findByInspectionIdAndRaterRole(inspectionId, raterRole).orElse(null);
    List<String> expectedKeys =
        activeDefinitionsFor(raterRole).stream()
            .map(RatingCriterionDefinitionResponse::code)
            .toList();

    boolean isUpdate = existing != null;
    InspectorRating saved;
    if (isUpdate) {
      existing.updateRating(
          actorId,
          request.overallScore(),
          request.criterionScores(),
          expectedKeys,
          request.comment(),
          now);
      saved = ratings.saveAndFlush(existing);
    } else {
      InspectorRating created =
          new InspectorRating(
              inspectionId,
              inspection.getInspectorId(),
              actorId,
              raterRole,
              request.overallScore(),
              request.criterionScores(),
              expectedKeys,
              request.comment(),
              now);
      saved = ratings.saveAndFlush(created);
    }

    eventPublisher.publishEvent(
        new InspectorRatedEvent(
            saved.getId(),
            saved.getInspectionId(),
            saved.getInspectorId(),
            actorId,
            raterRole.name(),
            saved.getOverallScore(),
            isUpdate,
            now));

    return buildInspectionBundle(actor, inspection);
  }

  @Transactional(readOnly = true)
  public InspectionRatingBundleResponse getInspectionRatings(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    Inspection inspection = findAccessibleInspection(actor, inspectionId, false);
    return buildInspectionBundle(actor, inspection);
  }

  @Transactional(readOnly = true)
  public InspectorRatingProfileResponse getInspectorSummaryAndHistory(
      UUID actorId, UUID inspectorId, Pageable pageable) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    if (inspectorId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_INSPECTOR", "Inspector ID is required.");
    }
    boolean isPrivileged = actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER);
    boolean isSelfInspector =
        actor.hasRole(Roles.INSPECTOR) && Objects.equals(actorId, inspectorId);
    boolean isCustomer = actor.hasRole(Roles.CUSTOMER);
    if (!isPrivileged && !isSelfInspector && !isCustomer) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "The user does not have permission to view inspector ratings.");
    }

    List<InspectorRating> allInspectorRatings =
        ratings.findByInspectorIdOrderByCreatedAtDesc(inspectorId);
    InspectorRatingSummaryResponse summary = buildSummary(inspectorId, allInspectorRatings);

    boolean historyVisible = isPrivileged || isSelfInspector;
    int safePage = pageable != null ? Math.max(pageable.getPageNumber(), 0) : 0;
    int safeSize = pageable != null ? Math.min(Math.max(pageable.getPageSize(), 1), 50) : 10;

    if (!historyVisible) {
      return new InspectorRatingProfileResponse(
          summary, List.of(), safePage, safeSize, 0L, 0, false);
    }

    Page<InspectorRating> page =
        ratings.findByInspectorIdOrderByCreatedAtDesc(
            inspectorId, PageRequest.of(safePage, safeSize));
    List<InspectorRating> content = page.getContent();
    Map<UUID, UserAccess.ActiveUser> usersCache = new HashMap<>();
    Map<UUID, AssetMeta> assetMetas = resolveAssetMeta(content);
    Instant now = Instant.now(clock);
    List<InspectorRatingResponse> items =
        content.stream()
            .map(
                rating -> {
                  AssetMeta meta = assetMetas.get(rating.getInspectionId());
                  return toResponse(
                      rating,
                      findUserCached(rating.getRaterUserId(), usersCache),
                      meta != null ? meta.assetName() : null,
                      meta != null ? meta.photoDocumentId() : null,
                      now);
                })
            .toList();

    return new InspectorRatingProfileResponse(
        summary,
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        true);
  }

  private InspectionRatingBundleResponse buildInspectionBundle(
      UserAccess.ActiveUser actor, Inspection inspection) {
    Instant now = Instant.now(clock);
    UUID inspectorId = inspection.getInspectorId();
    Map<UUID, UserAccess.ActiveUser> usersCache = new HashMap<>();
    UserAccess.ActiveUser inspectorUser =
        inspectorId != null ? findUserCached(inspectorId, usersCache) : null;
    String inspectorName = inspectorUser != null ? inspectorUser.fullName() : null;
    String inspectorAvatarUrl = inspectorUser != null ? inspectorUser.avatarUrl() : null;
    Asset singleAsset =
        inspection.getAssetId() != null
            ? assets
                .findDetailedById(inspection.getAssetId())
                .orElseGet(() -> assets.findById(inspection.getAssetId()).orElse(null))
            : null;
    String assetName = singleAsset != null ? singleAsset.getName() : null;
    UUID assetPhotoDocumentId = firstAssetPhotoId(singleAsset);

    List<InspectorRating> inspectionRatings =
        ratings.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
    InspectorRating customerEntity =
        inspectionRatings.stream()
            .filter(r -> r.getRaterRole() == RaterRole.CUSTOMER)
            .findFirst()
            .orElse(null);
    InspectorRating managerEntity =
        inspectionRatings.stream()
            .filter(r -> r.getRaterRole() == RaterRole.MANAGER)
            .findFirst()
            .orElse(null);

    InspectorRatingResponse customerDto =
        customerEntity != null
            ? toResponse(
                customerEntity,
                findUserCached(customerEntity.getRaterUserId(), usersCache),
                assetName,
                assetPhotoDocumentId,
                now)
            : null;
    InspectorRatingResponse managerDto =
        managerEntity != null
            ? toResponse(
                managerEntity,
                findUserCached(managerEntity.getRaterUserId(), usersCache),
                assetName,
                assetPhotoDocumentId,
                now)
            : null;

    boolean isOwningCustomer =
        actor.hasRole(Roles.CUSTOMER)
            && Objects.equals(inspection.getOwnerCustomerId(), actor.id());
    boolean isManager = actor.hasRole(Roles.MANAGER);
    boolean isAdminOnly = actor.hasRole(Roles.ADMIN) && !isManager && !isOwningCustomer;
    boolean isAssignedInspector =
        actor.hasRole(Roles.INSPECTOR) && Objects.equals(inspectorId, actor.id());

    String actorRoleLabel;
    RaterRole writableRole = null;
    if (isOwningCustomer && !isManager) {
      actorRoleLabel = RaterRole.CUSTOMER.name();
      writableRole = RaterRole.CUSTOMER;
    } else if (isManager) {
      actorRoleLabel = RaterRole.MANAGER.name();
      writableRole = RaterRole.MANAGER;
    } else if (isAdminOnly) {
      actorRoleLabel = Roles.ADMIN;
    } else if (isAssignedInspector) {
      actorRoleLabel = Roles.INSPECTOR;
    } else {
      actorRoleLabel = Roles.CUSTOMER;
    }

    InspectorRatingResponse myRating =
        writableRole == RaterRole.CUSTOMER
            ? customerDto
            : writableRole == RaterRole.MANAGER ? managerDto : null;

    boolean canRate = false;
    String ineligibleReason = null;
    if (writableRole == null) {
      ineligibleReason =
          isAdminOnly
              ? "Administrators have read-only access to inspector ratings."
              : "Inspectors cannot rate themselves.";
    } else if (inspectorId == null) {
      ineligibleReason = "No inspector is assigned to this inspection.";
    } else if (inspection.getStatus() != InspectionStatus.COMPLETED) {
      ineligibleReason = "Ratings open once the inspection report is published and completed.";
    } else if (myRating != null && !myRating.editable()) {
      ineligibleReason = "The 7-day rating edit window has expired.";
    } else {
      canRate = true;
    }

    Double averageRating = null;
    long ratingCount = 0L;
    if (inspectorId != null) {
      var aggregate = summarizeOneInspector(inspectorId);
      averageRating = aggregate.averageRating();
      ratingCount = aggregate.ratingCount();
    }

    boolean showManagerRating = isManager || isAdminOnly || isAssignedInspector;
    List<RatingCriterionDefinitionResponse> writableDefinitions =
        writableRole != null ? activeDefinitionsFor(writableRole) : List.of();
    List<String> writableKeys =
        writableDefinitions.stream().map(RatingCriterionDefinitionResponse::code).toList();
    return new InspectionRatingBundleResponse(
        inspection.getId(),
        inspectorId,
        inspectorName,
        inspectorAvatarUrl,
        canRate,
        ineligibleReason,
        actorRoleLabel,
        writableKeys,
        writableDefinitions,
        myRating != null ? myRating.editableUntil() : null,
        myRating,
        customerDto,
        showManagerRating ? managerDto : null,
        averageRating,
        ratingCount);
  }

  private AggregateTotals summarizeOneInspector(UUID inspectorId) {
    List<InspectorRatingRepository.InspectorRatingAggregateProjection> rows =
        ratings.summarizeByInspectorIds(List.of(inspectorId));
    long totalCount = 0L;
    long totalSum = 0L;
    for (var row : rows) {
      totalCount += row.getRatingCount();
      totalSum += row.getScoreSum();
    }
    Double avg = totalCount > 0 ? round2((double) totalSum / totalCount) : null;
    return new AggregateTotals(avg, totalCount);
  }

  private record AggregateTotals(Double averageRating, long ratingCount) {}

  private InspectorRatingSummaryResponse buildSummary(
      UUID inspectorId, List<InspectorRating> allRatings) {
    Map<Integer, Long> distribution = new LinkedHashMap<>();
    for (int star = 5; star >= 1; star--) {
      distribution.put(star, 0L);
    }
    long totalCount = 0L;
    long totalSum = 0L;
    long customerCount = 0L;
    long customerSum = 0L;
    long managerCount = 0L;
    long managerSum = 0L;

    List<RatingCriterionDefinitionResponse> definitions = allDefinitions();
    Map<String, long[]> customerCriteriaSums = new LinkedHashMap<>();
    Map<String, long[]> managerCriteriaSums = new LinkedHashMap<>();
    for (RatingCriterionDefinitionResponse def : definitions) {
      if (RaterRole.CUSTOMER.name().equals(def.raterRole())) {
        customerCriteriaSums.put(def.code(), new long[2]);
      } else if (RaterRole.MANAGER.name().equals(def.raterRole())) {
        managerCriteriaSums.put(def.code(), new long[2]);
      }
    }

    for (InspectorRating rating : allRatings) {
      int score = rating.getOverallScore();
      totalCount++;
      totalSum += score;
      distribution.computeIfPresent(score, (ignored, v) -> v + 1);
      if (rating.getRaterRole() == RaterRole.CUSTOMER) {
        customerCount++;
        customerSum += score;
        accumulateCriteria(customerCriteriaSums, rating.getCriterionScores());
      } else if (rating.getRaterRole() == RaterRole.MANAGER) {
        managerCount++;
        managerSum += score;
        accumulateCriteria(managerCriteriaSums, rating.getCriterionScores());
      }
    }

    return new InspectorRatingSummaryResponse(
        inspectorId,
        totalCount > 0 ? round2((double) totalSum / totalCount) : null,
        totalCount,
        customerCount > 0 ? round2((double) customerSum / customerCount) : null,
        customerCount,
        managerCount > 0 ? round2((double) managerSum / managerCount) : null,
        managerCount,
        distribution,
        toCriteriaAverages(customerCriteriaSums),
        toCriteriaAverages(managerCriteriaSums),
        definitions);
  }

  private List<RatingCriterionDefinitionResponse> activeDefinitionsFor(RaterRole role) {
    if (criteriaConfigs != null) {
      List<RatingCriterionConfig> configured =
          criteriaConfigs.findAllByRaterRoleAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(role);
      if (!configured.isEmpty()) {
        return configured.stream()
            .map(RatingCriterionConfigResponse::from)
            .map(RatingCriterionConfigResponse::toDefinition)
            .toList();
      }
    }
    return defaultDefinitions(role);
  }

  private List<RatingCriterionDefinitionResponse> allDefinitions() {
    if (criteriaConfigs != null) {
      List<RatingCriterionConfig> configured =
          criteriaConfigs.findAllByOrderByRaterRoleAscDisplayOrderAscCreatedAtAsc();
      if (!configured.isEmpty()) {
        return configured.stream()
            .map(RatingCriterionConfigResponse::from)
            .map(RatingCriterionConfigResponse::toDefinition)
            .toList();
      }
    }
    return java.util.stream.Stream.concat(
            defaultDefinitions(RaterRole.CUSTOMER).stream(),
            defaultDefinitions(RaterRole.MANAGER).stream())
        .toList();
  }

  private static List<RatingCriterionDefinitionResponse> defaultDefinitions(RaterRole role) {
    List<String> keys = role.expectedCriteriaKeys();
    java.util.List<RatingCriterionDefinitionResponse> list = new java.util.ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      String code = keys.get(i);
      list.add(
          new RatingCriterionDefinitionResponse(
              code,
              role.name(),
              defaultLabelEn(code),
              defaultLabelVi(code),
              null,
              null,
              i + 1,
              true));
    }
    return list;
  }

  private static String defaultLabelEn(String code) {
    return switch (code) {
      case "communication" -> "Communication";
      case "professionalism" -> "Professionalism";
      case "evidenceQuality" -> "Evidence & report";
      case "timeliness" -> "Timeliness";
      case "technicalAccuracy" -> "Technical accuracy";
      case "sopAndSafety" -> "SOP & safety";
      default -> code;
    };
  }

  private static String defaultLabelVi(String code) {
    return switch (code) {
      case "communication" -> "Giao tiếp & phối hợp";
      case "professionalism" -> "Tác phong chuyên nghiệp";
      case "evidenceQuality" -> "Chất lượng bằng chứng & báo cáo";
      case "timeliness" -> "Đúng tiến độ";
      case "technicalAccuracy" -> "Độ chính xác kỹ thuật";
      case "sopAndSafety" -> "Tuân thủ SOP & an toàn bay";
      default -> code;
    };
  }

  private static void accumulateCriteria(
      Map<String, long[]> sums, Map<String, Integer> criterionScores) {
    if (criterionScores == null) {
      return;
    }
    for (var entry : criterionScores.entrySet()) {
      Integer value = entry.getValue();
      if (value != null) {
        long[] bucket = sums.computeIfAbsent(entry.getKey(), ignored -> new long[2]);
        bucket[0] += value;
        bucket[1] += 1;
      }
    }
  }

  private static Map<String, Double> toCriteriaAverages(Map<String, long[]> sums) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (var entry : sums.entrySet()) {
      long count = entry.getValue()[1];
      if (count > 0) {
        out.put(entry.getKey(), round2((double) entry.getValue()[0] / count));
      }
    }
    return out;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private record AssetMeta(String assetName, UUID photoDocumentId) {}

  private Map<UUID, AssetMeta> resolveAssetMeta(List<InspectorRating> items) {
    Set<UUID> inspectionIds =
        items.stream()
            .map(InspectorRating::getInspectionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (inspectionIds.isEmpty()) {
      return Map.of();
    }
    List<Inspection> foundInspections = inspections.findAllById(inspectionIds);
    Set<UUID> assetIds =
        foundInspections.stream()
            .map(Inspection::getAssetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, Asset> assetsById = new HashMap<>();
    if (!assetIds.isEmpty()) {
      List<Asset> detailed = assets.findDetailedByIdIn(assetIds);
      List<Asset> loaded =
          detailed != null && !detailed.isEmpty() ? detailed : assets.findAllById(assetIds);
      for (Asset asset : loaded) {
        assetsById.put(asset.getId(), asset);
      }
    }
    Map<UUID, AssetMeta> result = new HashMap<>();
    for (Inspection inspection : foundInspections) {
      Asset asset = assetsById.get(inspection.getAssetId());
      if (asset != null) {
        result.put(inspection.getId(), new AssetMeta(asset.getName(), firstAssetPhotoId(asset)));
      }
    }
    return result;
  }

  private static UUID firstAssetPhotoId(Asset asset) {
    if (asset == null || asset.getDocuments() == null) {
      return null;
    }
    return asset.getDocuments().stream()
        .filter(
            doc ->
                doc.getId() != null
                    && doc.getContentType() != null
                    && doc.getContentType().startsWith("image/"))
        .sorted(
            java.util.Comparator.comparing(
                doc -> !"ASSET_PHOTO".equalsIgnoreCase(doc.getDocumentType())))
        .map(AssetDocument::getId)
        .findFirst()
        .orElse(null);
  }

  private InspectorRatingResponse toResponse(
      InspectorRating rating,
      UserAccess.ActiveUser rater,
      String assetName,
      UUID assetPhotoDocumentId,
      Instant now) {
    return new InspectorRatingResponse(
        rating.getId(),
        rating.getInspectionId(),
        rating.getInspectorId(),
        rating.getRaterUserId(),
        rater != null ? rater.fullName() : null,
        rater != null ? rater.avatarUrl() : null,
        rating.getRaterRole().name(),
        assetName,
        assetPhotoDocumentId,
        rating.getOverallScore(),
        rating.getCriterionScores(),
        rating.getComment(),
        rating.getCreatedAt(),
        rating.getUpdatedAt(),
        rating.editableUntil(),
        rating.isEditableAt(now));
  }

  private UserAccess.ActiveUser findUserCached(
      UUID userId, Map<UUID, UserAccess.ActiveUser> cache) {
    if (userId == null) {
      return null;
    }
    return cache.computeIfAbsent(userId, id -> userAccess.findUser(id).orElse(null));
  }

  private UserAccess.ActiveUser requireActiveUser(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "The user account is not active or does not exist."));
  }

  private Inspection findAccessibleInspection(
      UserAccess.ActiveUser actor, UUID inspectionId, boolean writeMode) {
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found."));

    boolean isManager = actor.hasRole(Roles.MANAGER);
    boolean isAdmin = actor.hasRole(Roles.ADMIN);
    boolean isOwningCustomer =
        actor.hasRole(Roles.CUSTOMER)
            && Objects.equals(inspection.getOwnerCustomerId(), actor.id());
    boolean isAssignedInspector =
        actor.hasRole(Roles.INSPECTOR) && Objects.equals(inspection.getInspectorId(), actor.id());

    if (writeMode) {
      if (isManager || isOwningCustomer) {
        return inspection;
      }
      if (actor.hasRole(Roles.CUSTOMER) && !isOwningCustomer && !isAdmin) {
        throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found.");
      }
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the owning customer or a manager can rate an inspector.");
    }

    if (isManager || isAdmin || isOwningCustomer || isAssignedInspector) {
      return inspection;
    }
    throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found.");
  }

  private RaterRole resolveWritableRaterRole(UserAccess.ActiveUser actor, Inspection inspection) {
    if (actor.hasRole(Roles.CUSTOMER)
        && Objects.equals(inspection.getOwnerCustomerId(), actor.id())
        && !actor.hasRole(Roles.MANAGER)) {
      return RaterRole.CUSTOMER;
    }
    if (actor.hasRole(Roles.MANAGER)) {
      return RaterRole.MANAGER;
    }
    if (actor.hasRole(Roles.CUSTOMER)
        && Objects.equals(inspection.getOwnerCustomerId(), actor.id())) {
      return RaterRole.CUSTOMER;
    }
    throw new BusinessException(
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "Administrators and inspectors cannot submit inspector ratings.");
  }
}
