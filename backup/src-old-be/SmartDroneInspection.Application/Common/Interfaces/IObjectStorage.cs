namespace SmartDroneInspection.Application.Common.Interfaces;

/// <summary>MinIO object storage abstraction for evidence and inspection images (spec NFR).</summary>
public interface IObjectStorage
{
    Task<string> UploadAsync(string bucket, string objectKey, Stream content, string contentType, CancellationToken ct = default);
    Task<Stream?> DownloadAsync(string bucket, string objectKey, CancellationToken ct = default);
    Task<string> GetPresignedUrlAsync(string bucket, string objectKey, TimeSpan expiry, CancellationToken ct = default);
    Task DeleteAsync(string bucket, string objectKey, CancellationToken ct = default);
}
