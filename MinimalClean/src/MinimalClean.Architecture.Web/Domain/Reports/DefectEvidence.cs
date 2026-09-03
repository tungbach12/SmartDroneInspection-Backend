using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Reports;

public class DefectEvidence : EntityBase<DefectEvidence, DefectEvidenceId>, IAggregateRoot
{
    private DefectEvidence() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public DefectEvidence(
        Guid defectId = default,
        string minioObjectKey = default!,
        string fileType = default!,
        long fileSizeBytes = default,
        string mimeType = default!,
        Guid uploadedByUserId = default,
        DateTime uploadedAt = default,
        string? thumbnailObjectKey = default!,
        string? caption = default!)  
    {
        DefectId = Guard.Against.Default(defectId, nameof(defectId));
        MinioObjectKey = Guard.Against.NullOrWhiteSpace(minioObjectKey, nameof(minioObjectKey));
        FileType = Guard.Against.NullOrWhiteSpace(fileType, nameof(fileType));
        FileSizeBytes = fileSizeBytes;
        MimeType = Guard.Against.NullOrWhiteSpace(mimeType, nameof(mimeType));
        UploadedByUserId = Guard.Against.Default(uploadedByUserId, nameof(uploadedByUserId));
        UploadedAt = uploadedAt;
        ThumbnailObjectKey = thumbnailObjectKey;
        Caption = caption;
    }

    public Guid DefectId { get; private set; }
    public string MinioObjectKey { get; private set; } = string.Empty;
    public string? ThumbnailObjectKey { get; private set; }
    public string FileType { get; private set; } = string.Empty;
    public long FileSizeBytes { get; private set; }
    public string MimeType { get; private set; } = string.Empty;
    public string? Caption { get; private set; }
    public Guid UploadedByUserId { get; private set; }
    public DateTime UploadedAt { get; private set; }

    public DefectEvidence UpdateDefectId(Guid newDefectId)
    {
        DefectId = newDefectId;
        return this;
    }

    public DefectEvidence UpdateMinioObjectKey(string newMinioObjectKey)
    {
        MinioObjectKey = Guard.Against.NullOrWhiteSpace(newMinioObjectKey, nameof(newMinioObjectKey));
        return this;
    }

    public DefectEvidence UpdateThumbnailObjectKey(string? newThumbnailObjectKey)
    {
        ThumbnailObjectKey = newThumbnailObjectKey;
        return this;
    }

    public DefectEvidence UpdateFileType(string newFileType)
    {
        FileType = Guard.Against.NullOrWhiteSpace(newFileType, nameof(newFileType));
        return this;
    }

    public DefectEvidence UpdateFileSizeBytes(long newFileSizeBytes)
    {
        FileSizeBytes = newFileSizeBytes;
        return this;
    }

    public DefectEvidence UpdateMimeType(string newMimeType)
    {
        MimeType = Guard.Against.NullOrWhiteSpace(newMimeType, nameof(newMimeType));
        return this;
    }

    public DefectEvidence UpdateCaption(string? newCaption)
    {
        Caption = newCaption;
        return this;
    }

    public DefectEvidence UpdateUploadedByUserId(Guid newUploadedByUserId)
    {
        UploadedByUserId = newUploadedByUserId;
        return this;
    }

    public DefectEvidence UpdateUploadedAt(DateTime newUploadedAt)
    {
        UploadedAt = newUploadedAt;
        return this;
    }

}
