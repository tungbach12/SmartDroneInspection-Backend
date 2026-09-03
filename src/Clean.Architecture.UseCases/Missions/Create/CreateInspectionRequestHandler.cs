using Ardalis.Result;
using Ardalis.SharedKernel;
using Clean.Architecture.Core.Missions;
using Clean.Architecture.Core.Missions.Enums;

namespace Clean.Architecture.UseCases.Missions.Create;

public class CreateInspectionRequestHandler(IRepository<InspectionRequest> repository) : IRequestHandler<CreateInspectionRequestCommand, Result<InspectionRequestId>>
{
    public async ValueTask<Result<InspectionRequestId>> Handle(CreateInspectionRequestCommand request, CancellationToken ct)
    {
        var req = new InspectionRequest(
            organizationId: request.OrganizationId,
            assetId: request.AssetId,
            requestedByUserId: request.RequestedByUserId,
            title: request.Title,
            description: request.Description,
            priority: request.Priority,
            status: InspectionRequestStatus.Pending);

        var created = await repository.AddAsync(req, ct);
        return created.Id;
    }
}
