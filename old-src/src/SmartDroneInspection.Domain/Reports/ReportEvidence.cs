using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class ReportEvidence : BaseEntity, ISoftDelete
{
    public Guid ReportId { get; set; }
    public string MinioObjectKey { get; set; } = string.Empty;
    public string? ThumbnailObjectKey { get; set; }
    public EvidenceFileType FileType { get; set; }
    public long FileSizeBytes { get; set; }
    public string MimeType { get; set; } = string.Empty;
    public string? Caption { get; set; }
    public Guid UploadedByUserId { get; set; }
    public DateTime UploadedAt { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
}
