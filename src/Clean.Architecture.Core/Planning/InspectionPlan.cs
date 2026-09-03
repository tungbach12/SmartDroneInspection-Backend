using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Planning.Enums;
using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Planning;

public class InspectionPlan : EntityBase<InspectionPlan, InspectionPlanId>, IAuditable, ISoftDelete, IAggregateRoot
{
    private InspectionPlan() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public InspectionPlan(
        Guid organizationId = default,
        Guid createdByUserId = default,
        string title = default!,
        FrequencyType frequencyType = default!,
        InspectionPlanPriority priority = default!,
        DateTime startDate = default,
        DateTime nextRunDate = default,
        string? description = default!,
        int frequencyInterval = 1,
        DateTime? endDate = default!,
        DateTime? lastRunDate = default!,
        InspectionPlanStatus status = default!,
        DateTime? activatedAt = default!,
        Guid? activatedByUserId = default!,
        DateTime? pausedAt = default!,
        string? pausedReason = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        CreatedByUserId = Guard.Against.Default(createdByUserId, nameof(createdByUserId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        FrequencyType = frequencyType;
        Priority = priority;
        StartDate = startDate;
        NextRunDate = nextRunDate;
        Description = description;
        FrequencyInterval = frequencyInterval;
        EndDate = endDate;
        LastRunDate = lastRunDate;
        Status = status;
        ActivatedAt = activatedAt;
        ActivatedByUserId = activatedByUserId;
        PausedAt = pausedAt;
        PausedReason = pausedReason;
    }

    public Guid OrganizationId { get; private set; }
    public Guid CreatedByUserId { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string? Description { get; private set; }
    public FrequencyType FrequencyType { get; private set; } = default!;
    public int FrequencyInterval { get; private set; } = 1;
    public InspectionPlanPriority Priority { get; private set; } = default!;
    public DateTime StartDate { get; private set; }
    public DateTime? EndDate { get; private set; }
    public DateTime NextRunDate { get; private set; }
    public DateTime? LastRunDate { get; private set; }
    public InspectionPlanStatus Status { get; private set; } = InspectionPlanStatus.Draft;
    public DateTime? ActivatedAt { get; private set; }
    public Guid? ActivatedByUserId { get; private set; }
    public DateTime? PausedAt { get; private set; }
    public string? PausedReason { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public InspectionPlan UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public InspectionPlan UpdateCreatedByUserId(Guid newCreatedByUserId)
    {
        CreatedByUserId = newCreatedByUserId;
        return this;
    }

    public InspectionPlan UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public InspectionPlan UpdateDescription(string? newDescription)
    {
        Description = newDescription;
        return this;
    }

    public InspectionPlan UpdateFrequencyType(FrequencyType newFrequencyType)
    {
        FrequencyType = newFrequencyType;
        return this;
    }

    public InspectionPlan UpdateFrequencyInterval(int newFrequencyInterval)
    {
        FrequencyInterval = newFrequencyInterval;
        return this;
    }

    public InspectionPlan UpdatePriority(InspectionPlanPriority newPriority)
    {
        Priority = newPriority;
        return this;
    }

    public InspectionPlan UpdateStartDate(DateTime newStartDate)
    {
        StartDate = newStartDate;
        return this;
    }

    public InspectionPlan UpdateEndDate(DateTime? newEndDate)
    {
        EndDate = newEndDate;
        return this;
    }

    public InspectionPlan UpdateNextRunDate(DateTime newNextRunDate)
    {
        NextRunDate = newNextRunDate;
        return this;
    }

    public InspectionPlan UpdateLastRunDate(DateTime? newLastRunDate)
    {
        LastRunDate = newLastRunDate;
        return this;
    }

    public InspectionPlan UpdateStatus(InspectionPlanStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public InspectionPlan UpdateActivatedAt(DateTime? newActivatedAt)
    {
        ActivatedAt = newActivatedAt;
        return this;
    }

    public InspectionPlan UpdateActivatedByUserId(Guid? newActivatedByUserId)
    {
        ActivatedByUserId = newActivatedByUserId;
        return this;
    }

    public InspectionPlan UpdatePausedAt(DateTime? newPausedAt)
    {
        PausedAt = newPausedAt;
        return this;
    }

    public InspectionPlan UpdatePausedReason(string? newPausedReason)
    {
        PausedReason = newPausedReason;
        return this;
    }

}
