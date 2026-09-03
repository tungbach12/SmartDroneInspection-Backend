using Ardalis.GuardClauses;
using Clean.Architecture.Core.Planning.Enums;

namespace Clean.Architecture.Core.Planning;

public class InspectionSchedule : EntityBase<InspectionSchedule, InspectionScheduleId>, IAggregateRoot
{
    private InspectionSchedule() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public InspectionSchedule(
        Guid planId = default,
        Guid assetId = default,
        DateTime scheduledDate = default,
        DateTime? scheduledEndDate = default!,
        Guid? inspectorId = default!,
        DateTime? assignedAt = default!,
        Guid? assignedByUserId = default!,
        ScheduleStatus status = default!,
        DateTime? completedAt = default!,
        DateTime? cancelledAt = default!,
        string? cancelledReason = default!,
        Guid? rescheduledFromId = default!)  
    {
        PlanId = Guard.Against.Default(planId, nameof(planId));
        AssetId = Guard.Against.Default(assetId, nameof(assetId));
        ScheduledDate = scheduledDate;
        ScheduledEndDate = scheduledEndDate;
        InspectorId = inspectorId;
        AssignedAt = assignedAt;
        AssignedByUserId = assignedByUserId;
        Status = status;
        CompletedAt = completedAt;
        CancelledAt = cancelledAt;
        CancelledReason = cancelledReason;
        RescheduledFromId = rescheduledFromId;
    }

    public Guid PlanId { get; private set; }
    public Guid AssetId { get; private set; }
    public DateTime ScheduledDate { get; private set; }
    public DateTime? ScheduledEndDate { get; private set; }
    public Guid? InspectorId { get; private set; }
    public DateTime? AssignedAt { get; private set; }
    public Guid? AssignedByUserId { get; private set; }
    public ScheduleStatus Status { get; private set; } = ScheduleStatus.Pending;
    public DateTime? CompletedAt { get; private set; }
    public DateTime? CancelledAt { get; private set; }
    public string? CancelledReason { get; private set; }
    public Guid? RescheduledFromId { get; private set; }

    public InspectionSchedule UpdatePlanId(Guid newPlanId)
    {
        PlanId = newPlanId;
        return this;
    }

    public InspectionSchedule UpdateAssetId(Guid newAssetId)
    {
        AssetId = newAssetId;
        return this;
    }

    public InspectionSchedule UpdateScheduledDate(DateTime newScheduledDate)
    {
        ScheduledDate = newScheduledDate;
        return this;
    }

    public InspectionSchedule UpdateScheduledEndDate(DateTime? newScheduledEndDate)
    {
        ScheduledEndDate = newScheduledEndDate;
        return this;
    }

    public InspectionSchedule UpdateInspectorId(Guid? newInspectorId)
    {
        InspectorId = newInspectorId;
        return this;
    }

    public InspectionSchedule UpdateAssignedAt(DateTime? newAssignedAt)
    {
        AssignedAt = newAssignedAt;
        return this;
    }

    public InspectionSchedule UpdateAssignedByUserId(Guid? newAssignedByUserId)
    {
        AssignedByUserId = newAssignedByUserId;
        return this;
    }

    public InspectionSchedule UpdateStatus(ScheduleStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public InspectionSchedule UpdateCompletedAt(DateTime? newCompletedAt)
    {
        CompletedAt = newCompletedAt;
        return this;
    }

    public InspectionSchedule UpdateCancelledAt(DateTime? newCancelledAt)
    {
        CancelledAt = newCancelledAt;
        return this;
    }

    public InspectionSchedule UpdateCancelledReason(string? newCancelledReason)
    {
        CancelledReason = newCancelledReason;
        return this;
    }

    public InspectionSchedule UpdateRescheduledFromId(Guid? newRescheduledFromId)
    {
        RescheduledFromId = newRescheduledFromId;
        return this;
    }

}
