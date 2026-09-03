using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class ReportFinding : BaseEntity
{
    public Guid ReportId { get; set; }
    public string Description { get; set; } = string.Empty;
    public DefectSeverity Severity { get; set; }
    public string? LocationNote { get; set; }
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public Guid? ImageId { get; set; }
    public JsonDocument? BoundingBox { get; set; }
    public decimal? ConfidenceScore { get; set; }
}
