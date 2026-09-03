using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Ai.Enums;
using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Ai;

public class KnowledgeCase : EntityBase<KnowledgeCase, KnowledgeCaseId>, IAuditable, IAggregateRoot
{
    private KnowledgeCase() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public KnowledgeCase(
        string title = default!,
        string content = default!,
        KnowledgeCaseType caseType = default!,
        KnowledgeCaseSource source = default!,
        bool isPublished = default,
        int usageCount = default,
        Guid? defectId = default!,
        Guid? reportId = default!,
        string? summary = default!,
        string language = "en",
        DateTime? publishedAt = default!,
        DateTime? lastUsedAt = default!)  
    {
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        Content = Guard.Against.NullOrWhiteSpace(content, nameof(content));
        CaseType = caseType;
        Source = source;
        IsPublished = isPublished;
        UsageCount = usageCount;
        DefectId = defectId;
        ReportId = reportId;
        Summary = summary;
        Language = Guard.Against.NullOrWhiteSpace(language, nameof(language));
        PublishedAt = publishedAt;
        LastUsedAt = lastUsedAt;
    }

    public Guid? DefectId { get; private set; }
    public Guid? ReportId { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string Content { get; private set; } = string.Empty;
    public string? Summary { get; private set; }
    public KnowledgeCaseType CaseType { get; private set; } = default!;
    public string Language { get; private set; } = "en";
    public KnowledgeCaseSource Source { get; private set; } = default!;
    public bool IsPublished { get; private set; }
    public DateTime? PublishedAt { get; private set; }
    public int UsageCount { get; private set; }
    public DateTime? LastUsedAt { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public List<string> Tags { get; private set; } = new();

    public KnowledgeCase UpdateDefectId(Guid? newDefectId)
    {
        DefectId = newDefectId;
        return this;
    }

    public KnowledgeCase UpdateReportId(Guid? newReportId)
    {
        ReportId = newReportId;
        return this;
    }

    public KnowledgeCase UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public KnowledgeCase UpdateContent(string newContent)
    {
        Content = Guard.Against.NullOrWhiteSpace(newContent, nameof(newContent));
        return this;
    }

    public KnowledgeCase UpdateSummary(string? newSummary)
    {
        Summary = newSummary;
        return this;
    }

    public KnowledgeCase UpdateCaseType(KnowledgeCaseType newCaseType)
    {
        CaseType = newCaseType;
        return this;
    }

    public KnowledgeCase UpdateLanguage(string newLanguage)
    {
        Language = Guard.Against.NullOrWhiteSpace(newLanguage, nameof(newLanguage));
        return this;
    }

    public KnowledgeCase UpdateSource(KnowledgeCaseSource newSource)
    {
        Source = newSource;
        return this;
    }

    public KnowledgeCase UpdateIsPublished(bool newIsPublished)
    {
        IsPublished = newIsPublished;
        return this;
    }

    public KnowledgeCase UpdatePublishedAt(DateTime? newPublishedAt)
    {
        PublishedAt = newPublishedAt;
        return this;
    }

    public KnowledgeCase UpdateUsageCount(int newUsageCount)
    {
        UsageCount = newUsageCount;
        return this;
    }

    public KnowledgeCase UpdateLastUsedAt(DateTime? newLastUsedAt)
    {
        LastUsedAt = newLastUsedAt;
        return this;
    }

}
