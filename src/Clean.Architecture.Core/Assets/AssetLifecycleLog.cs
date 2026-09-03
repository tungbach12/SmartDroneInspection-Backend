using Ardalis.GuardClauses;
using Clean.Architecture.Core.Assets.Enums;

namespace Clean.Architecture.Core.Assets;

public class AssetLifecycleLog : EntityBase<AssetLifecycleLog, AssetLifecycleLogId>, IAggregateRoot
{
    private AssetLifecycleLog() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public AssetLifecycleLog(
        Guid assetId = default,
        AssetStatus toStatus = default!,
        DateTime changedAt = default,
        AssetStatus? fromStatus = default!,
        Guid? changedBy = default!,
        string? reason = default!,
        string? note = default!)  
    {
        AssetId = Guard.Against.Default(assetId, nameof(assetId));
        ToStatus = toStatus;
        ChangedAt = changedAt;
        FromStatus = fromStatus;
        ChangedBy = changedBy;
        Reason = reason;
        Note = note;
    }

    public Guid AssetId { get; private set; }
    public AssetStatus? FromStatus { get; private set; }
    public AssetStatus ToStatus { get; private set; } = default!;
    public Guid? ChangedBy { get; private set; }
    public DateTime ChangedAt { get; private set; }
    public string? Reason { get; private set; }
    public string? Note { get; private set; }

    public AssetLifecycleLog UpdateAssetId(Guid newAssetId)
    {
        AssetId = newAssetId;
        return this;
    }

    public AssetLifecycleLog UpdateFromStatus(AssetStatus? newFromStatus)
    {
        FromStatus = newFromStatus;
        return this;
    }

    public AssetLifecycleLog UpdateToStatus(AssetStatus newToStatus)
    {
        ToStatus = newToStatus;
        return this;
    }

    public AssetLifecycleLog UpdateChangedBy(Guid? newChangedBy)
    {
        ChangedBy = newChangedBy;
        return this;
    }

    public AssetLifecycleLog UpdateChangedAt(DateTime newChangedAt)
    {
        ChangedAt = newChangedAt;
        return this;
    }

    public AssetLifecycleLog UpdateReason(string? newReason)
    {
        Reason = newReason;
        return this;
    }

    public AssetLifecycleLog UpdateNote(string? newNote)
    {
        Note = newNote;
        return this;
    }

}
