using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Common;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config;

internal static class EntityConfigurationExtensions
{
    public static void ConfigureBase<TEntity>(this EntityTypeBuilder<TEntity> builder, string tableName)
        where TEntity : class
    {
        builder.ToTable(tableName);
        builder.HasKey("Id");
        builder.Property("Id").ValueGeneratedNever();
        builder.Property<DateTime>("CreatedAt").IsRequired();
        builder.Property<DateTime?>("UpdatedAt");
    }

    public static void ConfigureAudit<TEntity>(this EntityTypeBuilder<TEntity> builder)
        where TEntity : class, IAuditable
    {
        builder.Property(entity => entity.CreatedBy);
        builder.Property(entity => entity.UpdatedBy);
    }

    public static void ConfigureSoftDelete<TEntity>(this EntityTypeBuilder<TEntity> builder)
        where TEntity : class, ISoftDelete
    {
        builder.Property(entity => entity.IsDeleted).HasDefaultValue(false);
        builder.Property(entity => entity.DeletedAt);
        builder.Property(entity => entity.DeletedBy);
        builder.HasIndex(entity => entity.IsDeleted);
    }
}