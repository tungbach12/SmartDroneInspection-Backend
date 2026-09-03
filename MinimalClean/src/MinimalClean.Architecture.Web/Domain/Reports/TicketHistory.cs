using Ardalis.GuardClauses;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;

namespace MinimalClean.Architecture.Web.Domain.Reports;

public class TicketHistory : EntityBase<TicketHistory, TicketHistoryId>, IAggregateRoot
{
    private TicketHistory() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public TicketHistory(
        Guid ticketId = default,
        TicketStatus toStatus = default!,
        Guid changedByUserId = default,
        DateTime changedAt = default,
        TicketStatus? fromStatus = default!,
        string? comment = default!,
        int? timeSpentMinutes = default!)  
    {
        TicketId = Guard.Against.Default(ticketId, nameof(ticketId));
        ToStatus = toStatus;
        ChangedByUserId = Guard.Against.Default(changedByUserId, nameof(changedByUserId));
        ChangedAt = changedAt;
        FromStatus = fromStatus;
        Comment = comment;
        TimeSpentMinutes = timeSpentMinutes;
    }

    public Guid TicketId { get; private set; }
    public TicketStatus? FromStatus { get; private set; }
    public TicketStatus ToStatus { get; private set; } = default!;
    public Guid ChangedByUserId { get; private set; }
    public DateTime ChangedAt { get; private set; }
    public string? Comment { get; private set; }
    public int? TimeSpentMinutes { get; private set; }

    public TicketHistory UpdateTicketId(Guid newTicketId)
    {
        TicketId = newTicketId;
        return this;
    }

    public TicketHistory UpdateFromStatus(TicketStatus? newFromStatus)
    {
        FromStatus = newFromStatus;
        return this;
    }

    public TicketHistory UpdateToStatus(TicketStatus newToStatus)
    {
        ToStatus = newToStatus;
        return this;
    }

    public TicketHistory UpdateChangedByUserId(Guid newChangedByUserId)
    {
        ChangedByUserId = newChangedByUserId;
        return this;
    }

    public TicketHistory UpdateChangedAt(DateTime newChangedAt)
    {
        ChangedAt = newChangedAt;
        return this;
    }

    public TicketHistory UpdateComment(string? newComment)
    {
        Comment = newComment;
        return this;
    }

    public TicketHistory UpdateTimeSpentMinutes(int? newTimeSpentMinutes)
    {
        TimeSpentMinutes = newTimeSpentMinutes;
        return this;
    }

}
