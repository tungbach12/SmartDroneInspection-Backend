namespace MinimalClean.Architecture.Web.Domain.Common;

/// <summary>
/// Audit trail: which user created/updated this row.
/// Stamped automatically by <c>ApplicationDbContext.SaveChangesAsync</c>.
/// </summary>
public interface IAuditable
{
    Guid? CreatedBy { get; set; }
    Guid? UpdatedBy { get; set; }
}
