using MinimalClean.Architecture.Web.Domain.Common;
using MinimalClean.Architecture.Web.Domain.Assets.Enums;
using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Assets;

public class AssetDocument : EntityBase<AssetDocument, AssetDocumentId>, IAuditable, ISoftDelete
{
    private AssetDocument() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public AssetDocument(
        Guid assetId = default,
        string title = default!,
        string fileKey = default!,
        AssetDocumentType fileType = default!,
        string mimeType = default!,
        Guid uploadedBy = default,
        long? fileSizeBytes = default!,
        DateTime? documentDate = default!)  
    {
        AssetId = Guard.Against.Default(assetId, nameof(assetId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        FileKey = Guard.Against.NullOrWhiteSpace(fileKey, nameof(fileKey));
        FileType = fileType;
        MimeType = Guard.Against.NullOrWhiteSpace(mimeType, nameof(mimeType));
        UploadedBy = Guard.Against.Default(uploadedBy, nameof(uploadedBy));
        FileSizeBytes = fileSizeBytes;
        DocumentDate = documentDate;
    }

    public Guid AssetId { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string FileKey { get; private set; } = string.Empty;
    public AssetDocumentType FileType { get; private set; } = default!;
    public long? FileSizeBytes { get; private set; }
    public string MimeType { get; private set; } = string.Empty;
    public Guid UploadedBy { get; private set; }
    public DateTime? DocumentDate { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public AssetDocument UpdateAssetId(Guid newAssetId)
    {
        AssetId = newAssetId;
        return this;
    }

    public AssetDocument UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public AssetDocument UpdateFileKey(string newFileKey)
    {
        FileKey = Guard.Against.NullOrWhiteSpace(newFileKey, nameof(newFileKey));
        return this;
    }

    public AssetDocument UpdateFileType(AssetDocumentType newFileType)
    {
        FileType = newFileType;
        return this;
    }

    public AssetDocument UpdateFileSizeBytes(long? newFileSizeBytes)
    {
        FileSizeBytes = newFileSizeBytes;
        return this;
    }

    public AssetDocument UpdateMimeType(string newMimeType)
    {
        MimeType = Guard.Against.NullOrWhiteSpace(newMimeType, nameof(newMimeType));
        return this;
    }

    public AssetDocument UpdateUploadedBy(Guid newUploadedBy)
    {
        UploadedBy = newUploadedBy;
        return this;
    }

    public AssetDocument UpdateDocumentDate(DateTime? newDocumentDate)
    {
        DocumentDate = newDocumentDate;
        return this;
    }

}
