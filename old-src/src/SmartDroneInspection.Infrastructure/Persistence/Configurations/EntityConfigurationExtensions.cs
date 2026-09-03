using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations;

internal static class EntityConfigurationExtensions
{
    public static void ConfigureBase<TEntity>(this EntityTypeBuilder<TEntity> builder, string tableName)
        where TEntity : BaseEntity
    {
        builder.ToTable(tableName);
        builder.HasKey(entity => entity.Id);
        builder.Property(entity => entity.Id).ValueGeneratedNever();
        builder.Property(entity => entity.CreatedAt).IsRequired();
        builder.Property(entity => entity.UpdatedAt);
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
