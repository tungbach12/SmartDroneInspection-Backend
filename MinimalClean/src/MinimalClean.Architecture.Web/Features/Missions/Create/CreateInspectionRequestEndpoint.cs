using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Missions;
using MinimalClean.Architecture.Web.Domain.Missions.Enums;

namespace MinimalClean.Architecture.Web.Features.Missions.Create;

public sealed class CreateInspectionRequestRequest
{
    public Guid OrganizationId { get; init; }
    public Guid AssetId { get; init; }
    public Guid RequestedByUserId { get; init; }
    public string Title { get; init; } = string.Empty;
    public string Description { get; init; } = string.Empty;
    public InspectionRequestPriority Priority { get; init; } = InspectionRequestPriority.Medium;
}

public sealed class CreateInspectionRequestValidator : Validator<CreateInspectionRequestRequest>
{
    public CreateInspectionRequestValidator()
    {
        RuleFor(x => x.Title).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Description).NotEmpty();
        RuleFor(x => x.AssetId).NotEmpty();
    }
}

public sealed class CreateInspectionRequestEndpoint(IRepository<InspectionRequest> repository) : Endpoint<CreateInspectionRequestRequest, Results<Created<InspectionRequestDto>, ValidationProblem, ProblemHttpResult>>
{
    public override void Configure()
    {
        Post("/missions/requests");
        AllowAnonymous();
        Tags("Missions");
        Summary(s =>
        {
            s.Summary = "Create inspection request (Minimal - vertical slice)";
            s.Description = "Creates request directly in Web project, no separate UseCases project — Minimal CA style.";
        });
    }

    public override async Task<Results<Created<InspectionRequestDto>, ValidationProblem, ProblemHttpResult>> ExecuteAsync(CreateInspectionRequestRequest req, CancellationToken ct)
    {
        var entity = new InspectionRequest(
            organizationId: req.OrganizationId,
            assetId: req.AssetId,
            requestedByUserId: req.RequestedByUserId,
            title: req.Title,
            description: req.Description,
            priority: req.Priority,
            status: InspectionRequestStatus.Pending);

        await repository.AddAsync(entity, ct);
        await repository.SaveChangesAsync(ct);

        var dto = new InspectionRequestDto(entity.Id.Value, entity.AssetId, entity.Title, entity.Status.Name, entity.CreatedAt);
        return TypedResults.Created($"/missions/requests/{entity.Id.Value}", dto);
    }
}

public record InspectionRequestDto(Guid Id, Guid AssetId, string Title, string Status, DateTime CreatedAt);
