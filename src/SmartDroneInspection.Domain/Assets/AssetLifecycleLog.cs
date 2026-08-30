using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Assets;

public class AssetLifecycleLog : BaseEntity
{
    public Guid AssetId { get; set; }
    public AssetStatus? FromStatus { get; set; }
    public AssetStatus ToStatus { get; set; }
    public Guid? ChangedBy { get; set; }
    public DateTime ChangedAt { get; set; }
    public string? Reason { get; set; }
    public string? Note { get; set; }
}
