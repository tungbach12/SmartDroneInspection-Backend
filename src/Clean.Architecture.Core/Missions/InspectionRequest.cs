using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Missions.Enums;
using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Missions;

public class InspectionRequest : EntityBase<InspectionRequest, InspectionRequestId>, IAuditable, IAggregateRoot
{
    private InspectionRequest() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public InspectionRequest(
        Guid organizationId = default,
        Guid assetId = default,
        Guid requestedByUserId = default,
        string title = default!,
        string description = default!,
        InspectionRequestPriority priority = default!,
        Guid? inspectorId = default!,
        Guid? planId = default!,
        InspectionRequestStatus status = default!,
        Guid? decidedByUserId = default!,
        DateTime? decidedAt = default!,
        string? rejectReason = default!,
        double? latitude = default!,
        double? longitude = default!,
        string? locationOverride = default!,
        DateTime? requestedCompletionDate = default!,
        DateTime? actualCompletionDate = default!,
        Guid? missionCreationKey = default!,
        int? estimatedDurationMinutes = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        AssetId = Guard.Against.Default(assetId, nameof(assetId));
        RequestedByUserId = Guard.Against.Default(requestedByUserId, nameof(requestedByUserId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        Description = Guard.Against.NullOrWhiteSpace(description, nameof(description));
        Priority = priority;
        InspectorId = inspectorId;
        PlanId = planId;
        Status = status;
        DecidedByUserId = decidedByUserId;
        DecidedAt = decidedAt;
        RejectReason = rejectReason;
        Latitude = latitude;
        Longitude = longitude;
        LocationOverride = locationOverride;
        RequestedCompletionDate = requestedCompletionDate;
        ActualCompletionDate = actualCompletionDate;
        MissionCreationKey = missionCreationKey;
        EstimatedDurationMinutes = estimatedDurationMinutes;
    }

    public Guid OrganizationId { get; private set; }
    public Guid AssetId { get; private set; }
    public Guid RequestedByUserId { get; private set; }
    public Guid? InspectorId { get; private set; }
    public Guid? PlanId { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string Description { get; private set; } = string.Empty;
    public InspectionRequestPriority Priority { get; private set; } = default!;
    public InspectionRequestStatus Status { get; private set; } = InspectionRequestStatus.Pending;
    public Guid? DecidedByUserId { get; private set; }
    public DateTime? DecidedAt { get; private set; }
    public string? RejectReason { get; private set; }
    public double? Latitude { get; private set; }
    public double? Longitude { get; private set; }
    public string? LocationOverride { get; private set; }
    public DateTime? RequestedCompletionDate { get; private set; }
    public DateTime? ActualCompletionDate { get; private set; }
    public Guid? MissionCreationKey { get; private set; }
    public int? EstimatedDurationMinutes { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }

    public InspectionRequest UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public InspectionRequest UpdateAssetId(Guid newAssetId)
    {
        AssetId = newAssetId;
        return this;
    }

    public InspectionRequest UpdateRequestedByUserId(Guid newRequestedByUserId)
    {
        RequestedByUserId = newRequestedByUserId;
        return this;
    }

    public InspectionRequest UpdateInspectorId(Guid? newInspectorId)
    {
        InspectorId = newInspectorId;
        return this;
    }

    public InspectionRequest UpdatePlanId(Guid? newPlanId)
    {
        PlanId = newPlanId;
        return this;
    }

    public InspectionRequest UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public InspectionRequest UpdateDescription(string newDescription)
    {
        Description = Guard.Against.NullOrWhiteSpace(newDescription, nameof(newDescription));
        return this;
    }

    public InspectionRequest UpdatePriority(InspectionRequestPriority newPriority)
    {
        Priority = newPriority;
        return this;
    }

    public InspectionRequest UpdateStatus(InspectionRequestStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public InspectionRequest UpdateDecidedByUserId(Guid? newDecidedByUserId)
    {
        DecidedByUserId = newDecidedByUserId;
        return this;
    }

    public InspectionRequest UpdateDecidedAt(DateTime? newDecidedAt)
    {
        DecidedAt = newDecidedAt;
        return this;
    }

    public InspectionRequest UpdateRejectReason(string? newRejectReason)
    {
        RejectReason = newRejectReason;
        return this;
    }

    public InspectionRequest UpdateLatitude(double? newLatitude)
    {
        Latitude = newLatitude;
        return this;
    }

    public InspectionRequest UpdateLongitude(double? newLongitude)
    {
        Longitude = newLongitude;
        return this;
    }

    public InspectionRequest UpdateLocationOverride(string? newLocationOverride)
    {
        LocationOverride = newLocationOverride;
        return this;
    }

    public InspectionRequest UpdateRequestedCompletionDate(DateTime? newRequestedCompletionDate)
    {
        RequestedCompletionDate = newRequestedCompletionDate;
        return this;
    }

    public InspectionRequest UpdateActualCompletionDate(DateTime? newActualCompletionDate)
    {
        ActualCompletionDate = newActualCompletionDate;
        return this;
    }

    public InspectionRequest UpdateMissionCreationKey(Guid? newMissionCreationKey)
    {
        MissionCreationKey = newMissionCreationKey;
        return this;
    }

    public InspectionRequest UpdateEstimatedDurationMinutes(int? newEstimatedDurationMinutes)
    {
        EstimatedDurationMinutes = newEstimatedDurationMinutes;
        return this;
    }

}
