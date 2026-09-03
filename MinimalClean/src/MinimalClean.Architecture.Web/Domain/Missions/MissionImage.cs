using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Missions;

public class MissionImage : EntityBase<MissionImage, MissionImageId>, IAggregateRoot
{
    private MissionImage() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public MissionImage(
        Guid droneMissionId = default,
        string minioObjectKey = default!,
        long fileSizeBytes = default,
        string mimeType = default!,
        DateTime capturedAt = default,
        bool aiAnalyzed = default,
        string? thumbnailObjectKey = default!,
        int? widthPx = default!,
        int? heightPx = default!,
        double? latitude = default!,
        double? longitude = default!,
        double? altitudeMeters = default!,
        double? headingDegrees = default!,
        double? cameraAngleDegrees = default!)  
    {
        DroneMissionId = Guard.Against.Default(droneMissionId, nameof(droneMissionId));
        MinioObjectKey = Guard.Against.NullOrWhiteSpace(minioObjectKey, nameof(minioObjectKey));
        FileSizeBytes = fileSizeBytes;
        MimeType = Guard.Against.NullOrWhiteSpace(mimeType, nameof(mimeType));
        CapturedAt = capturedAt;
        AiAnalyzed = aiAnalyzed;
        ThumbnailObjectKey = thumbnailObjectKey;
        WidthPx = widthPx;
        HeightPx = heightPx;
        Latitude = latitude;
        Longitude = longitude;
        AltitudeMeters = altitudeMeters;
        HeadingDegrees = headingDegrees;
        CameraAngleDegrees = cameraAngleDegrees;
    }

    public Guid DroneMissionId { get; private set; }
    public string MinioObjectKey { get; private set; } = string.Empty;
    public string? ThumbnailObjectKey { get; private set; }
    public long FileSizeBytes { get; private set; }
    public string MimeType { get; private set; } = string.Empty;
    public int? WidthPx { get; private set; }
    public int? HeightPx { get; private set; }
    public DateTime CapturedAt { get; private set; }
    public double? Latitude { get; private set; }
    public double? Longitude { get; private set; }
    public double? AltitudeMeters { get; private set; }
    public double? HeadingDegrees { get; private set; }
    public double? CameraAngleDegrees { get; private set; }
    public bool AiAnalyzed { get; private set; }

    public MissionImage UpdateDroneMissionId(Guid newDroneMissionId)
    {
        DroneMissionId = newDroneMissionId;
        return this;
    }

    public MissionImage UpdateMinioObjectKey(string newMinioObjectKey)
    {
        MinioObjectKey = Guard.Against.NullOrWhiteSpace(newMinioObjectKey, nameof(newMinioObjectKey));
        return this;
    }

    public MissionImage UpdateThumbnailObjectKey(string? newThumbnailObjectKey)
    {
        ThumbnailObjectKey = newThumbnailObjectKey;
        return this;
    }

    public MissionImage UpdateFileSizeBytes(long newFileSizeBytes)
    {
        FileSizeBytes = newFileSizeBytes;
        return this;
    }

    public MissionImage UpdateMimeType(string newMimeType)
    {
        MimeType = Guard.Against.NullOrWhiteSpace(newMimeType, nameof(newMimeType));
        return this;
    }

    public MissionImage UpdateWidthPx(int? newWidthPx)
    {
        WidthPx = newWidthPx;
        return this;
    }

    public MissionImage UpdateHeightPx(int? newHeightPx)
    {
        HeightPx = newHeightPx;
        return this;
    }

    public MissionImage UpdateCapturedAt(DateTime newCapturedAt)
    {
        CapturedAt = newCapturedAt;
        return this;
    }

    public MissionImage UpdateLatitude(double? newLatitude)
    {
        Latitude = newLatitude;
        return this;
    }

    public MissionImage UpdateLongitude(double? newLongitude)
    {
        Longitude = newLongitude;
        return this;
    }

    public MissionImage UpdateAltitudeMeters(double? newAltitudeMeters)
    {
        AltitudeMeters = newAltitudeMeters;
        return this;
    }

    public MissionImage UpdateHeadingDegrees(double? newHeadingDegrees)
    {
        HeadingDegrees = newHeadingDegrees;
        return this;
    }

    public MissionImage UpdateCameraAngleDegrees(double? newCameraAngleDegrees)
    {
        CameraAngleDegrees = newCameraAngleDegrees;
        return this;
    }

    public MissionImage UpdateAiAnalyzed(bool newAiAnalyzed)
    {
        AiAnalyzed = newAiAnalyzed;
        return this;
    }

}
