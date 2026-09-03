namespace SmartDroneInspection.Application.Common.Models;

/// <summary>
/// Base for list queries. SortBy is whitelisted per-module (never raw dynamic OrderBy — SQL injection risk).
/// </summary>
public record PagedQuery
{
    private const int MaxPageSize = 100;

    public int Page { get; init; } = 1;
    public int PageSize { get; init; } = 20;
    public string? Search { get; init; }
    public string? SortBy { get; init; }
    public bool SortDescending { get; init; } = true;

    public int Skip => (Page - 1) * PageSize;
    public int Take => Math.Min(PageSize, MaxPageSize);
}
