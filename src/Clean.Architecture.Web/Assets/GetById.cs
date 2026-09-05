using Clean.Architecture.Core.Assets;
using Clean.Architecture.UseCases.Assets;
using Clean.Architecture.UseCases.Assets.Get;
using Clean.Architecture.Web.Extensions;
using Microsoft.AspNetCore.Http.HttpResults;

namespace Clean.Architecture.Web.Assets;

public class GetById(IMediator mediator)
  : Endpoint<GetAssetByIdRequest,
             Results<Ok<AssetRecord>, NotFound, ProblemHttpResult>,
             GetAssetByIdMapper>
{
  public override void Configure()
  {
    Get(GetAssetByIdRequest.Route);
    AllowAnonymous();

    Summary(s =>
    {
      s.Summary = "Get an asset by ID";
      s.Description = "Retrieves a specific asset by its unique identifier. Returns asset metadata including code, name, and status.";
      s.ExampleRequest = new GetAssetByIdRequest { AssetId = Guid.NewGuid() };
      s.ResponseExamples[200] = new AssetRecord(
        Guid.NewGuid(), "AST-001", "Power Transformer A1", "Active", "Hanoi, Vietnam");

      s.Responses[200] = "Asset found and returned successfully";
      s.Responses[404] = "Asset with specified ID not found";
    });

    Tags("Assets");

    Description(builder => builder
      .Accepts<GetAssetByIdRequest>()
      .Produces<AssetRecord>(200, "application/json")
      .ProducesProblem(404));
  }

  public override async Task<Results<Ok<AssetRecord>, NotFound, ProblemHttpResult>>
    ExecuteAsync(GetAssetByIdRequest request, CancellationToken ct)
  {
    var result = await mediator.Send(new GetAssetQuery(AssetId.From(request.AssetId)), ct);

    return result.ToGetByIdResult(Map.FromEntity);
  }
}

public sealed class GetAssetByIdMapper
  : Mapper<GetAssetByIdRequest, AssetRecord, AssetDto>
{
  public override AssetRecord FromEntity(AssetDto e)
    => new(e.Id.Value, e.Code, e.Name, e.Status, e.Address ?? string.Empty);
}
