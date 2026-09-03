namespace MinimalClean.Architecture.Web.Domain.Common;

/// <summary>
/// Optimistic concurrency: Version is incremented by the SaveChanges interceptor
/// on every modification and configured as an EF concurrency token, so a stale
/// save throws <see cref="Microsoft.EntityFrameworkCore.DbUpdateConcurrencyException"/>.
/// </summary>
public interface IHasVersion
{
    int Version { get; set; }
}
