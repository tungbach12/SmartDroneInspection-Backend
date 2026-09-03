using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Reports.Enums;
using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Reports;

public class ReportEvidence : EntityBase<ReportEvidence, ReportEvidenceId>, ISoftDelete, IAggregateRoot
{
    private ReportEvidence() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public ReportEvidence(
        Guid reportId = default,
        string minioObjectKey = default!,
        EvidenceFileType fileType = default!,
        long fileSizeBytes = default,
        string mimeType = default!,
        Guid uploadedByUserId = default,
        DateTime uploadedAt = default,
        string? thumbnailObjectKey = default!,
        string? caption = default!)  
    {
        ReportId = Guard.Against.Default(reportId, nameof(reportId));
        MinioObjectKey = Guard.Against.NullOrWhiteSpace(minioObjectKey, nameof(minioObjectKey));
        FileType = fileType;
        FileSizeBytes = fileSizeBytes;
        MimeType = Guard.Against.NullOrWhiteSpace(mimeType, nameof(mimeType));
        UploadedByUserId = Guard.Against.Default(uploadedByUserId, nameof(uploadedByUserId));
        UploadedAt = uploadedAt;
        ThumbnailObjectKey = thumbnailObjectKey;
        Caption = caption;
    }

    public Guid ReportId { get; private set; }
    public string MinioObjectKey { get; private set; } = string.Empty;
    public string? ThumbnailObjectKey { get; private set; }
    public EvidenceFileType FileType { get; private set; } = default!;
    public long FileSizeBytes { get; private set; }
    public string MimeType { get; private set; } = string.Empty;
    public string? Caption { get; private set; }
    public Guid UploadedByUserId { get; private set; }
    public DateTime UploadedAt { get; private set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public ReportEvidence UpdateReportId(Guid newReportId)
    {
        ReportId = newReportId;
        return this;
    }

    public ReportEvidence UpdateMinioObjectKey(string newMinioObjectKey)
    {
        MinioObjectKey = Guard.Against.NullOrWhiteSpace(newMinioObjectKey, nameof(newMinioObjectKey));
        return this;
    }

    public ReportEvidence UpdateThumbnailObjectKey(string? newThumbnailObjectKey)
    {
        ThumbnailObjectKey = newThumbnailObjectKey;
        return this;
    }

    public ReportEvidence UpdateFileType(EvidenceFileType newFileType)
    {
        FileType = newFileType;
        return this;
    }

    public ReportEvidence UpdateFileSizeBytes(long newFileSizeBytes)
    {
        FileSizeBytes = newFileSizeBytes;
        return this;
    }

    public ReportEvidence UpdateMimeType(string newMimeType)
    {
        MimeType = Guard.Against.NullOrWhiteSpace(newMimeType, nameof(newMimeType));
        return this;
    }

    public ReportEvidence UpdateCaption(string? newCaption)
    {
        Caption = newCaption;
        return this;
    }

    public ReportEvidence UpdateUploadedByUserId(Guid newUploadedByUserId)
    {
        UploadedByUserId = newUploadedByUserId;
        return this;
    }

    public ReportEvidence UpdateUploadedAt(DateTime newUploadedAt)
    {
        UploadedAt = newUploadedAt;
        return this;
    }

}
