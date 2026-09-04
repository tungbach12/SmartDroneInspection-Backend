namespace MinimalClean.Architecture.Web.Features.Tickets;

public sealed record MaintenanceTicketDto(
    Guid Id,
    string? TicketNumber,
    string Title,
    string Description,
    string Priority,
    string Status,
    Guid? DefectId,
    Guid? AssignedToUserId,
    DateTime? DueDate,
    DateTime CreatedAt);
