using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Assets;

public sealed class AssetConfiguration : IEntityTypeConfiguration<Asset>
{
    public void Configure(EntityTypeBuilder<Asset> builder)
    {
        builder.ConfigureBase("assets"); builder.ConfigureAudit(); builder.ConfigureSoftDelete();
        builder.Property(x => x.Code).HasMaxLength(100).IsRequired(); builder.Property(x => x.NormalizedCode).HasMaxLength(100).IsRequired();
        builder.Property(x => x.Name).HasMaxLength(250).IsRequired(); builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.CountryCode).HasMaxLength(2);
        builder.Property(x => x.Address).HasMaxLength(500); builder.Property(x => x.Region).HasMaxLength(150);
        builder.Property(x => x.Metadata).HasColumnType("jsonb"); builder.Property(x => x.Specifications).HasColumnType("jsonb");
        builder.Property(x => x.Tags).HasColumnType("text[]"); builder.HasIndex(x => new { x.OrganizationId, x.NormalizedCode }).IsUnique();
        builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => new { x.Latitude, x.Longitude });
        builder.ToTable("assets", table =>
        {
            table.HasCheckConstraint("ck_assets_coordinates", "(latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)");
            table.HasCheckConstraint("ck_assets_country_code", "country_code IS NULL OR country_code ~ '^[A-Z]{2}$'");
        });
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<AssetCategory>().WithMany().HasForeignKey(x => x.CategoryId).OnDelete(DeleteBehavior.SetNull);
    }
}
