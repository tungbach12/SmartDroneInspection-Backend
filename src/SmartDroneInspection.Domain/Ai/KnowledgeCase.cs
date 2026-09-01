using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Ai;

public class KnowledgeCase : BaseEntity, IAuditable
{
    public Guid? DefectId { get; set; }
    public Guid? ReportId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Content { get; set; } = string.Empty;
    public string? Summary { get; set; }
    public KnowledgeCaseType CaseType { get; set; }
    public List<string> Tags { get; private set; } = new();
    public string Language { get; set; } = "en";
    public KnowledgeCaseSource Source { get; set; }
    public bool IsPublished { get; set; }
    public DateTime? PublishedAt { get; set; }
    public int UsageCount { get; set; }
    public DateTime? LastUsedAt { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
}
