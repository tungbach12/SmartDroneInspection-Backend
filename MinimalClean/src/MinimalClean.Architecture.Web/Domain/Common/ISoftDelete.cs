namespace MinimalClean.Architecture.Web.Domain.Common;

/// <summary>
/// Soft delete: row is hidden from queries (global query filter in DbContext)
/// but physically retained for audit / history.
/// </summary>
public interface ISoftDelete
{
    bool IsDeleted { get; set; }
    DateTime? DeletedAt { get; set; }
    Guid? DeletedBy { get; set; }
}
