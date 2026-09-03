using Vogen;

namespace Clean.Architecture.Core.Assets;

[ValueObject<Guid>]
public readonly partial struct AssetLifecycleLogId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AssetLifecycleLogId cannot be empty.");
}
