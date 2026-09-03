using MinimalClean.Architecture.Web.Domain.Common;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;
using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Reports;

public class MaintenanceTicket : EntityBase<MaintenanceTicket, MaintenanceTicketId>, IAuditable, IAggregateRoot
{
    private MaintenanceTicket() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public MaintenanceTicket(
        Guid organizationId = default,
        Guid createdByUserId = default,
        string title = default!,
        string description = default!,
        TicketPriority priority = default!,
        Guid? defectId = default!,
        Guid? requestId = default!,
        Guid? assignedToUserId = default!,
        DateTime? assignedAt = default!,
        Guid? assignedByUserId = default!,
        string? ticketNumber = default!,
        TicketStatus status = default!,
        DateTime? dueDate = default!,
        DateTime? startedAt = default!,
        DateTime? resolvedAt = default!,
        DateTime? closedAt = default!,
        string? resolutionNotes = default!,
        decimal? estimatedCost = default!,
        decimal? actualCost = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        CreatedByUserId = Guard.Against.Default(createdByUserId, nameof(createdByUserId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        Description = Guard.Against.NullOrWhiteSpace(description, nameof(description));
        Priority = priority;
        DefectId = defectId;
        RequestId = requestId;
        AssignedToUserId = assignedToUserId;
        AssignedAt = assignedAt;
        AssignedByUserId = assignedByUserId;
        TicketNumber = ticketNumber;
        Status = status;
        DueDate = dueDate;
        StartedAt = startedAt;
        ResolvedAt = resolvedAt;
        ClosedAt = closedAt;
        ResolutionNotes = resolutionNotes;
        EstimatedCost = estimatedCost;
        ActualCost = actualCost;
    }

    public Guid OrganizationId { get; private set; }
    public Guid? DefectId { get; private set; }
    public Guid? RequestId { get; private set; }
    public Guid? AssignedToUserId { get; private set; }
    public DateTime? AssignedAt { get; private set; }
    public Guid? AssignedByUserId { get; private set; }
    public Guid CreatedByUserId { get; private set; }
    public string? TicketNumber { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string Description { get; private set; } = string.Empty;
    public TicketPriority Priority { get; private set; } = default!;
    public TicketStatus Status { get; private set; } = TicketStatus.Open;
    public DateTime? DueDate { get; private set; }
    public DateTime? StartedAt { get; private set; }
    public DateTime? ResolvedAt { get; private set; }
    public DateTime? ClosedAt { get; private set; }
    public string? ResolutionNotes { get; private set; }
    public decimal? EstimatedCost { get; private set; }
    public decimal? ActualCost { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }

    public MaintenanceTicket UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public MaintenanceTicket UpdateDefectId(Guid? newDefectId)
    {
        DefectId = newDefectId;
        return this;
    }

    public MaintenanceTicket UpdateRequestId(Guid? newRequestId)
    {
        RequestId = newRequestId;
        return this;
    }

    public MaintenanceTicket UpdateAssignedToUserId(Guid? newAssignedToUserId)
    {
        AssignedToUserId = newAssignedToUserId;
        return this;
    }

    public MaintenanceTicket UpdateAssignedAt(DateTime? newAssignedAt)
    {
        AssignedAt = newAssignedAt;
        return this;
    }

    public MaintenanceTicket UpdateAssignedByUserId(Guid? newAssignedByUserId)
    {
        AssignedByUserId = newAssignedByUserId;
        return this;
    }

    public MaintenanceTicket UpdateCreatedByUserId(Guid newCreatedByUserId)
    {
        CreatedByUserId = newCreatedByUserId;
        return this;
    }

    public MaintenanceTicket UpdateTicketNumber(string? newTicketNumber)
    {
        TicketNumber = newTicketNumber;
        return this;
    }

    public MaintenanceTicket UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public MaintenanceTicket UpdateDescription(string newDescription)
    {
        Description = Guard.Against.NullOrWhiteSpace(newDescription, nameof(newDescription));
        return this;
    }

    public MaintenanceTicket UpdatePriority(TicketPriority newPriority)
    {
        Priority = newPriority;
        return this;
    }

    public MaintenanceTicket UpdateStatus(TicketStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public MaintenanceTicket UpdateDueDate(DateTime? newDueDate)
    {
        DueDate = newDueDate;
        return this;
    }

    public MaintenanceTicket UpdateStartedAt(DateTime? newStartedAt)
    {
        StartedAt = newStartedAt;
        return this;
    }

    public MaintenanceTicket UpdateResolvedAt(DateTime? newResolvedAt)
    {
        ResolvedAt = newResolvedAt;
        return this;
    }

    public MaintenanceTicket UpdateClosedAt(DateTime? newClosedAt)
    {
        ClosedAt = newClosedAt;
        return this;
    }

    public MaintenanceTicket UpdateResolutionNotes(string? newResolutionNotes)
    {
        ResolutionNotes = newResolutionNotes;
        return this;
    }

    public MaintenanceTicket UpdateEstimatedCost(decimal? newEstimatedCost)
    {
        EstimatedCost = newEstimatedCost;
        return this;
    }

    public MaintenanceTicket UpdateActualCost(decimal? newActualCost)
    {
        ActualCost = newActualCost;
        return this;
    }

}
