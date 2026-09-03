using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Planning;

public class InspectionCalendarEvent : EntityBase<InspectionCalendarEvent, InspectionCalendarEventId>, IAggregateRoot
{
    private InspectionCalendarEvent() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public InspectionCalendarEvent(
        string title = default!,
        DateTime eventDate = default,
        bool allDay = default,
        Guid createdByUserId = default,
        Guid? planId = default!,
        Guid? requestId = default!,
        Guid? scheduleId = default!,
        string? description = default!,
        DateTime? endDate = default!,
        string? location = default!,
        string? recurrenceRule = default!,
        Guid? recurrenceParentId = default!)  
    {
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        EventDate = eventDate;
        AllDay = allDay;
        CreatedByUserId = Guard.Against.Default(createdByUserId, nameof(createdByUserId));
        PlanId = planId;
        RequestId = requestId;
        ScheduleId = scheduleId;
        Description = description;
        EndDate = endDate;
        Location = location;
        RecurrenceRule = recurrenceRule;
        RecurrenceParentId = recurrenceParentId;
    }

    public Guid? PlanId { get; private set; }
    public Guid? RequestId { get; private set; }
    public Guid? ScheduleId { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string? Description { get; private set; }
    public DateTime EventDate { get; private set; }
    public DateTime? EndDate { get; private set; }
    public bool AllDay { get; private set; }
    public string? Location { get; private set; }
    public string? RecurrenceRule { get; private set; }
    public Guid? RecurrenceParentId { get; private set; }
    public Guid CreatedByUserId { get; private set; }

    public InspectionCalendarEvent UpdatePlanId(Guid? newPlanId)
    {
        PlanId = newPlanId;
        return this;
    }

    public InspectionCalendarEvent UpdateRequestId(Guid? newRequestId)
    {
        RequestId = newRequestId;
        return this;
    }

    public InspectionCalendarEvent UpdateScheduleId(Guid? newScheduleId)
    {
        ScheduleId = newScheduleId;
        return this;
    }

    public InspectionCalendarEvent UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public InspectionCalendarEvent UpdateDescription(string? newDescription)
    {
        Description = newDescription;
        return this;
    }

    public InspectionCalendarEvent UpdateEventDate(DateTime newEventDate)
    {
        EventDate = newEventDate;
        return this;
    }

    public InspectionCalendarEvent UpdateEndDate(DateTime? newEndDate)
    {
        EndDate = newEndDate;
        return this;
    }

    public InspectionCalendarEvent UpdateAllDay(bool newAllDay)
    {
        AllDay = newAllDay;
        return this;
    }

    public InspectionCalendarEvent UpdateLocation(string? newLocation)
    {
        Location = newLocation;
        return this;
    }

    public InspectionCalendarEvent UpdateRecurrenceRule(string? newRecurrenceRule)
    {
        RecurrenceRule = newRecurrenceRule;
        return this;
    }

    public InspectionCalendarEvent UpdateRecurrenceParentId(Guid? newRecurrenceParentId)
    {
        RecurrenceParentId = newRecurrenceParentId;
        return this;
    }

    public InspectionCalendarEvent UpdateCreatedByUserId(Guid newCreatedByUserId)
    {
        CreatedByUserId = newCreatedByUserId;
        return this;
    }

}
