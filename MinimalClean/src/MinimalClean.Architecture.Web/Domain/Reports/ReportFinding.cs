using Ardalis.GuardClauses;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;
using System.Text.Json;

namespace MinimalClean.Architecture.Web.Domain.Reports;

public class ReportFinding : EntityBase<ReportFinding, ReportFindingId>, IAggregateRoot
{
    private ReportFinding() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public ReportFinding(
        Guid reportId = default,
        string description = default!,
        DefectSeverity severity = default!,
        string? locationNote = default!,
        double? latitude = default!,
        double? longitude = default!,
        Guid? imageId = default!,
        JsonDocument? boundingBox = default!,
        decimal? confidenceScore = default!)  
    {
        ReportId = Guard.Against.Default(reportId, nameof(reportId));
        Description = Guard.Against.NullOrWhiteSpace(description, nameof(description));
        Severity = severity;
        LocationNote = locationNote;
        Latitude = latitude;
        Longitude = longitude;
        ImageId = imageId;
        BoundingBox = boundingBox;
        ConfidenceScore = confidenceScore;
    }

    public Guid ReportId { get; private set; }
    public string Description { get; private set; } = string.Empty;
    public DefectSeverity Severity { get; private set; } = default!;
    public string? LocationNote { get; private set; }
    public double? Latitude { get; private set; }
    public double? Longitude { get; private set; }
    public Guid? ImageId { get; private set; }
    public JsonDocument? BoundingBox { get; private set; }
    public decimal? ConfidenceScore { get; private set; }

    public ReportFinding UpdateReportId(Guid newReportId)
    {
        ReportId = newReportId;
        return this;
    }

    public ReportFinding UpdateDescription(string newDescription)
    {
        Description = Guard.Against.NullOrWhiteSpace(newDescription, nameof(newDescription));
        return this;
    }

    public ReportFinding UpdateSeverity(DefectSeverity newSeverity)
    {
        Severity = newSeverity;
        return this;
    }

    public ReportFinding UpdateLocationNote(string? newLocationNote)
    {
        LocationNote = newLocationNote;
        return this;
    }

    public ReportFinding UpdateLatitude(double? newLatitude)
    {
        Latitude = newLatitude;
        return this;
    }

    public ReportFinding UpdateLongitude(double? newLongitude)
    {
        Longitude = newLongitude;
        return this;
    }

    public ReportFinding UpdateImageId(Guid? newImageId)
    {
        ImageId = newImageId;
        return this;
    }

    public ReportFinding UpdateBoundingBox(JsonDocument? newBoundingBox)
    {
        BoundingBox = newBoundingBox;
        return this;
    }

    public ReportFinding UpdateConfidenceScore(decimal? newConfidenceScore)
    {
        ConfidenceScore = newConfidenceScore;
        return this;
    }

}
