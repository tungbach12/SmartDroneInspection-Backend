using FastEndpoints;
using FluentValidation;

namespace Clean.Architecture.Web.Assets;

/// <summary>
/// See: https://fast-endpoints.com/docs/validation
/// </summary>
public class GetAssetValidator : Validator<GetAssetByIdRequest>
{
  public GetAssetValidator()
  {
    RuleFor(x => x.AssetId)
      .NotEqual(Guid.Empty);
  }
}
