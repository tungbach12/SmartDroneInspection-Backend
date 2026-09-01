using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class DefectEvidence : BaseEntity
{
    public Guid DefectId { get; set; }
    public string MinioObjectKey { get; set; } = string.Empty;
    public string? ThumbnailObjectKey { get; set; }
    public string FileType { get; set; } = string.Empty;
    public long FileSizeBytes { get; set; }
    public string MimeType { get; set; } = string.Empty;
    public string? Caption { get; set; }
    public Guid UploadedByUserId { get; set; }
    public DateTime UploadedAt { get; set; }
}
