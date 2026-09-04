using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Reports;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;

namespace MinimalClean.Architecture.Web.Features.Tickets.Create;

public sealed record CreateMaintenanceTicketRequest
{
    public Guid OrganizationId { get; init; }
    public Guid CreatedByUserId { get; init; }
    public string Title { get; init; } = string.Empty;
    public string Description { get; init; } = string.Empty;
    public string Priority { get; init; } = "Medium";
    public Guid? DefectId { get; init; }
    public Guid? RequestId { get; init; }
    public Guid? AssignedToUserId { get; init; }
    public DateTime? DueDate { get; init; }
    public decimal? EstimatedCost { get; init; }
}

public sealed class CreateMaintenanceTicketValidator : Validator<CreateMaintenanceTicketRequest>
{
    public CreateMaintenanceTicketValidator()
    {
        RuleFor(x => x.Title).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Description).NotEmpty();
        RuleFor(x => x.CreatedByUserId).NotEmpty();
    }
}

public sealed class CreateMaintenanceTicketEndpoint(IRepository<MaintenanceTicket> repository)
    : Endpoint<CreateMaintenanceTicketRequest, Results<Created<MaintenanceTicketDto>, ValidationProblem, ProblemHttpResult>>
{
    public override void Configure()
    {
        Post("/tickets");
        AllowAnonymous();
        Tags("Tickets");
        Summary(s =>
        {
            s.Summary = "Create maintenance work ticket";
            s.Description = "Generates a maintenance ticket assigned to an engineer from an identified defect.";
        });
    }

    public override async Task<Results<Created<MaintenanceTicketDto>, ValidationProblem, ProblemHttpResult>> ExecuteAsync(CreateMaintenanceTicketRequest req, CancellationToken ct)
    {
        var ticketNumber = $"TCK-{DateTime.UtcNow:yyyyMMdd}-{Random.Shared.Next(1000, 9999)}";

        TicketPriority priority = TicketPriority.Medium;
        if (!string.IsNullOrWhiteSpace(req.Priority) && TicketPriority.TryFromName(req.Priority, true, out var parsed))
        {
            priority = parsed;
        }

        var ticket = new MaintenanceTicket(
            organizationId: req.OrganizationId,
            createdByUserId: req.CreatedByUserId,
            title: req.Title,
            description: req.Description,
            priority: priority,
            defectId: req.DefectId,
            requestId: req.RequestId,
            assignedToUserId: req.AssignedToUserId,
            assignedAt: req.AssignedToUserId.HasValue ? DateTime.UtcNow : null,
            ticketNumber: ticketNumber,
            status: TicketStatus.Open,
            dueDate: req.DueDate,
            estimatedCost: req.EstimatedCost);

        await repository.AddAsync(ticket, ct);
        await repository.SaveChangesAsync(ct);

        var dto = new MaintenanceTicketDto(
            ticket.Id.Value,
            ticket.TicketNumber,
            ticket.Title,
            ticket.Description,
            ticket.Priority.Name,
            ticket.Status.Name,
            ticket.DefectId,
            ticket.AssignedToUserId,
            ticket.DueDate,
            ticket.CreatedAt);

        return TypedResults.Created($"/tickets/{ticket.Id.Value}", dto);
    }
}
