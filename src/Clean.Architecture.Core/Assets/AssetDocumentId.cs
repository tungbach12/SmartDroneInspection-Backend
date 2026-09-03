using Vogen;

namespace Clean.Architecture.Core.Assets;

[ValueObject<Guid>]
public readonly partial struct AssetDocumentId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AssetDocumentId cannot be empty.");
}
