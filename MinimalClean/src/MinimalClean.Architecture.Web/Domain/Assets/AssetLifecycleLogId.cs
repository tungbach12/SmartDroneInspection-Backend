using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Assets;

[ValueObject<Guid>]
public readonly partial struct AssetLifecycleLogId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AssetLifecycleLogId cannot be empty.");
}
