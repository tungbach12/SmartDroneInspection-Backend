using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Assets;

public class AssetDocument : BaseEntity, IAuditable, ISoftDelete
{
    public Guid AssetId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string FileKey { get; set; } = string.Empty;
    public AssetDocumentType FileType { get; set; }
    public long? FileSizeBytes { get; set; }
    public string MimeType { get; set; } = string.Empty;
    public Guid UploadedBy { get; set; }
    public DateTime? DocumentDate { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
}
