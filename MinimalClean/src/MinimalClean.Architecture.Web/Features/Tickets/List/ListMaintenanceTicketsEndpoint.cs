using Ardalis.Specification;
using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Reports;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;

namespace MinimalClean.Architecture.Web.Features.Tickets.List;

public sealed class PagedMaintenanceTicketsSpec : Specification<MaintenanceTicket>
{
    public PagedMaintenanceTicketsSpec(
        Guid organizationId,
        int page,
        int pageSize,
        Guid? assignedToUserId = null,
        TicketStatus? status = null)
    {
        var query = Query.Where(t => t.OrganizationId == organizationId);

        if (assignedToUserId.HasValue)
        {
            query.Where(t => t.AssignedToUserId == assignedToUserId.Value);
        }

        if (status is not null)
        {
            query.Where(t => t.Status == status);
        }

        var skip = Math.Max(0, (page - 1) * pageSize);
        var take = Math.Clamp(pageSize, 1, 100);

        query
            .OrderByDescending(t => t.CreatedAt)
            .Skip(skip)
            .Take(take);
    }
}

public sealed record ListMaintenanceTicketsRequest
{
    public Guid OrganizationId { get; init; }
    public int Page { get; init; } = 1;
    public int PageSize { get; init; } = 20;
    public Guid? AssignedToUserId { get; init; }
    public string? Status { get; init; }
}

public sealed record ListMaintenanceTicketsResponse(
    IReadOnlyList<MaintenanceTicketDto> Items,
    int Page,
    int PageSize,
    int TotalCount);

public sealed class ListMaintenanceTicketsEndpoint(IRepository<MaintenanceTicket> repository)
    : Endpoint<ListMaintenanceTicketsRequest, Ok<ListMaintenanceTicketsResponse>>
{
    public override void Configure()
    {
        Get("/tickets");
        AllowAnonymous();
        Tags("Tickets");
        Summary(s =>
        {
            s.Summary = "List paginated maintenance tickets";
            s.Description = "Retrieves maintenance tickets with optional status and assigned user filtering.";
        });
    }

    public override async Task<Ok<ListMaintenanceTicketsResponse>> ExecuteAsync(ListMaintenanceTicketsRequest req, CancellationToken ct)
    {
        TicketStatus? statusEnum = null;
        if (!string.IsNullOrWhiteSpace(req.Status) && TicketStatus.TryFromName(req.Status, true, out var parsed))
        {
            statusEnum = parsed;
        }

        var spec = new PagedMaintenanceTicketsSpec(
            req.OrganizationId,
            req.Page,
            req.PageSize,
            req.AssignedToUserId,
            statusEnum);

        var tickets = await repository.ListAsync(spec, ct);

        var countSpec = new PagedMaintenanceTicketsSpec(
            req.OrganizationId,
            1,
            int.MaxValue,
            req.AssignedToUserId,
            statusEnum);
        var totalCount = await repository.CountAsync(countSpec, ct);

        var dtos = tickets.Select(t => new MaintenanceTicketDto(
            t.Id.Value,
            t.TicketNumber,
            t.Title,
            t.Description,
            t.Priority.Name,
            t.Status.Name,
            t.DefectId,
            t.AssignedToUserId,
            t.DueDate,
            t.CreatedAt)).ToList();

        return TypedResults.Ok(new ListMaintenanceTicketsResponse(dtos, req.Page, req.PageSize, totalCount));
    }
}
