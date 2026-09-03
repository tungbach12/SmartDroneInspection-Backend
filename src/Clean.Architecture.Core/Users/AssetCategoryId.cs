using Vogen;

namespace Clean.Architecture.Core.Users;

[ValueObject<Guid>]
public readonly partial struct AssetCategoryId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AssetCategoryId cannot be empty.");
}
