using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Missions;

public class MissionImage : BaseEntity
{
    public Guid DroneMissionId { get; set; }
    public string MinioObjectKey { get; set; } = string.Empty;
    public string? ThumbnailObjectKey { get; set; }
    public long FileSizeBytes { get; set; }
    public string MimeType { get; set; } = string.Empty;
    public int? WidthPx { get; set; }
    public int? HeightPx { get; set; }
    public DateTime CapturedAt { get; set; }
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public double? AltitudeMeters { get; set; }
    public double? HeadingDegrees { get; set; }
    public double? CameraAngleDegrees { get; set; }
    public bool AiAnalyzed { get; set; }
}
