using Ardalis.Specification;
using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Missions;
using MinimalClean.Architecture.Web.Features.Missions.Create;

namespace MinimalClean.Architecture.Web.Features.Missions.GetById;

public sealed class InspectionRequestByIdSpec : Specification<InspectionRequest>
{
    public InspectionRequestByIdSpec(Guid organizationId, Guid id)
    {
        Query.Where(r => r.OrganizationId == organizationId && r.Id == InspectionRequestId.From(id));
    }
}

public sealed record GetInspectionRequestByIdRequest
{
    public Guid OrganizationId { get; init; }
    public Guid Id { get; init; }
}

public sealed class GetInspectionRequestByIdEndpoint(IRepository<InspectionRequest> repository)
    : Endpoint<GetInspectionRequestByIdRequest, Results<Ok<InspectionRequestDto>, NotFound>>
{
    public override void Configure()
    {
        Get("/missions/requests/{id:guid}");
        AllowAnonymous();
        Tags("Missions");
        Summary(s =>
        {
            s.Summary = "Get inspection request by ID";
            s.Description = "Retrieves an inspection request details.";
        });
    }

    public override async Task<Results<Ok<InspectionRequestDto>, NotFound>> ExecuteAsync(GetInspectionRequestByIdRequest req, CancellationToken ct)
    {
        var spec = new InspectionRequestByIdSpec(req.OrganizationId, req.Id);
        var entity = await repository.FirstOrDefaultAsync(spec, ct);

        if (entity is null)
        {
            return TypedResults.NotFound();
        }

        var dto = new InspectionRequestDto(
            entity.Id.Value,
            entity.AssetId,
            entity.Title,
            entity.Status.Name,
            entity.CreatedAt);

        return TypedResults.Ok(dto);
    }
}
