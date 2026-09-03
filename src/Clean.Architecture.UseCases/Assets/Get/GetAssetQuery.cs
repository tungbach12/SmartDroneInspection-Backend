using Ardalis.Result;
using Clean.Architecture.Core.Assets;
using Mediator;

namespace Clean.Architecture.UseCases.Assets.Get;

public record GetAssetQuery(AssetId AssetId) : IRequest<Result<AssetDto>>;
