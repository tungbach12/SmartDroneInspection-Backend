using Vogen;

namespace Clean.Architecture.Core.Assets;

[ValueObject<Guid>]
public readonly partial struct AssetId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AssetId cannot be empty.");
}
