using Ardalis.Result;
using Clean.Architecture.Core.Missions;
using Clean.Architecture.Core.Missions.Enums;
using Mediator;

namespace Clean.Architecture.UseCases.Missions.Create;

public record CreateInspectionRequestCommand(
    Guid OrganizationId,
    Guid AssetId,
    Guid RequestedByUserId,
    string Title,
    string Description,
    InspectionRequestPriority Priority) : IRequest<Result<InspectionRequestId>>;
