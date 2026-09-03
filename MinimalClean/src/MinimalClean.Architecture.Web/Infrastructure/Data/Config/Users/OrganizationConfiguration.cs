using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data.Config;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Users;

public sealed class OrganizationConfiguration : IEntityTypeConfiguration<Organization>
{
    public void Configure(EntityTypeBuilder<Organization> builder)
    {
        builder.ConfigureBase("organizations");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit(); builder.ConfigureSoftDelete();
        builder.Property(x => x.Name).HasMaxLength(200).IsRequired();
        builder.Property(x => x.Code).HasMaxLength(50).IsRequired();
        builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.ContactEmail).HasMaxLength(320);
        builder.Property(x => x.ContactPhone).HasMaxLength(32);
        builder.Property(x => x.Address).HasMaxLength(500);
        builder.HasIndex(x => x.Name).IsUnique(); builder.HasIndex(x => x.Code).IsUnique();
    }
}