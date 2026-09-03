using MinimalClean.Architecture.Web.Domain.Common;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;
using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Reports;

public class Defect : EntityBase<Defect, DefectId>, IAuditable, IAggregateRoot
{
    private Defect() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public Defect(
        Guid organizationId = default,
        Guid reportId = default,
        Guid assetId = default,
        string title = default!,
        string description = default!,
        DefectSeverity severity = default!,
        DefectCategory category = default!,
        RepairPriority repairPriority = default!,
        DateTime detectedAt = default,
        Guid? findingId = default!,
        string? defectNumber = default!,
        string? repairRecommendation = default!,
        decimal? estimatedRepairCost = default!,
        int? estimatedRepairHours = default!,
        DefectStatus status = default!,
        DateTime? confirmedAt = default!,
        Guid? confirmedByUserId = default!,
        DateTime? resolvedAt = default!,
        Guid? resolvedByUserId = default!,
        string? resolutionNotes = default!,
        DateTime? closedAt = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        ReportId = Guard.Against.Default(reportId, nameof(reportId));
        AssetId = Guard.Against.Default(assetId, nameof(assetId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        Description = Guard.Against.NullOrWhiteSpace(description, nameof(description));
        Severity = severity;
        Category = category;
        RepairPriority = repairPriority;
        DetectedAt = detectedAt;
        FindingId = findingId;
        DefectNumber = defectNumber;
        RepairRecommendation = repairRecommendation;
        EstimatedRepairCost = estimatedRepairCost;
        EstimatedRepairHours = estimatedRepairHours;
        Status = status;
        ConfirmedAt = confirmedAt;
        ConfirmedByUserId = confirmedByUserId;
        ResolvedAt = resolvedAt;
        ResolvedByUserId = resolvedByUserId;
        ResolutionNotes = resolutionNotes;
        ClosedAt = closedAt;
    }

    public Guid OrganizationId { get; private set; }
    public Guid? FindingId { get; private set; }
    public Guid ReportId { get; private set; }
    public Guid AssetId { get; private set; }
    public string? DefectNumber { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string Description { get; private set; } = string.Empty;
    public DefectSeverity Severity { get; private set; } = default!;
    public DefectCategory Category { get; private set; } = default!;
    public string? RepairRecommendation { get; private set; }
    public RepairPriority RepairPriority { get; private set; } = default!;
    public decimal? EstimatedRepairCost { get; private set; }
    public int? EstimatedRepairHours { get; private set; }
    public DefectStatus Status { get; private set; } = DefectStatus.Open;
    public DateTime DetectedAt { get; private set; }
    public DateTime? ConfirmedAt { get; private set; }
    public Guid? ConfirmedByUserId { get; private set; }
    public DateTime? ResolvedAt { get; private set; }
    public Guid? ResolvedByUserId { get; private set; }
    public string? ResolutionNotes { get; private set; }
    public DateTime? ClosedAt { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }

    public Defect UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public Defect UpdateFindingId(Guid? newFindingId)
    {
        FindingId = newFindingId;
        return this;
    }

    public Defect UpdateReportId(Guid newReportId)
    {
        ReportId = newReportId;
        return this;
    }

    public Defect UpdateAssetId(Guid newAssetId)
    {
        AssetId = newAssetId;
        return this;
    }

    public Defect UpdateDefectNumber(string? newDefectNumber)
    {
        DefectNumber = newDefectNumber;
        return this;
    }

    public Defect UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public Defect UpdateDescription(string newDescription)
    {
        Description = Guard.Against.NullOrWhiteSpace(newDescription, nameof(newDescription));
        return this;
    }

    public Defect UpdateSeverity(DefectSeverity newSeverity)
    {
        Severity = newSeverity;
        return this;
    }

    public Defect UpdateCategory(DefectCategory newCategory)
    {
        Category = newCategory;
        return this;
    }

    public Defect UpdateRepairRecommendation(string? newRepairRecommendation)
    {
        RepairRecommendation = newRepairRecommendation;
        return this;
    }

    public Defect UpdateRepairPriority(RepairPriority newRepairPriority)
    {
        RepairPriority = newRepairPriority;
        return this;
    }

    public Defect UpdateEstimatedRepairCost(decimal? newEstimatedRepairCost)
    {
        EstimatedRepairCost = newEstimatedRepairCost;
        return this;
    }

    public Defect UpdateEstimatedRepairHours(int? newEstimatedRepairHours)
    {
        EstimatedRepairHours = newEstimatedRepairHours;
        return this;
    }

    public Defect UpdateStatus(DefectStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public Defect UpdateDetectedAt(DateTime newDetectedAt)
    {
        DetectedAt = newDetectedAt;
        return this;
    }

    public Defect UpdateConfirmedAt(DateTime? newConfirmedAt)
    {
        ConfirmedAt = newConfirmedAt;
        return this;
    }

    public Defect UpdateConfirmedByUserId(Guid? newConfirmedByUserId)
    {
        ConfirmedByUserId = newConfirmedByUserId;
        return this;
    }

    public Defect UpdateResolvedAt(DateTime? newResolvedAt)
    {
        ResolvedAt = newResolvedAt;
        return this;
    }

    public Defect UpdateResolvedByUserId(Guid? newResolvedByUserId)
    {
        ResolvedByUserId = newResolvedByUserId;
        return this;
    }

    public Defect UpdateResolutionNotes(string? newResolutionNotes)
    {
        ResolutionNotes = newResolutionNotes;
        return this;
    }

    public Defect UpdateClosedAt(DateTime? newClosedAt)
    {
        ClosedAt = newClosedAt;
        return this;
    }

}
