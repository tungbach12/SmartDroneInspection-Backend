using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Assets;

public class Asset : BaseEntity, IAuditable, ISoftDelete
{
    public Guid OrganizationId { get; set; }
    public string Code { get; set; } = string.Empty;
    public string NormalizedCode { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public Guid? CategoryId { get; set; }
    public AssetStatus Status { get; set; } = AssetStatus.Active;
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public double? AltitudeMeters { get; set; }
    public string? Address { get; set; }
    public string? Region { get; set; }
    public string? CountryCode { get; set; }
    public DateTime? InstallationDate { get; set; }
    public DateTime? LastInspectedAt { get; set; }
    public DateTime? NextInspectionDueAt { get; set; }
    public JsonDocument? Metadata { get; set; }
    public JsonDocument? Specifications { get; set; }
    public string[]? Tags { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
}
