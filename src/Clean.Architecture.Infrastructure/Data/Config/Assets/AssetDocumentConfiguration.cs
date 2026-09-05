using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Assets;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;
using Clean.Architecture.Core.Assets.Enums;

namespace Clean.Architecture.Infrastructure.Data.Config.Assets;

public sealed class AssetDocumentConfiguration : IEntityTypeConfiguration<AssetDocument>
{
    public void Configure(EntityTypeBuilder<AssetDocument> builder)
    {
        builder.ConfigureBase("asset_documents");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit(); builder.ConfigureSoftDelete();
        builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.FileKey).HasMaxLength(1024).IsRequired();
        builder.Property(x => x.FileType).HasConversion(SmartEnumStringConverter.Create<AssetDocumentType>()).HasMaxLength(32); builder.Property(x => x.MimeType).HasMaxLength(255).IsRequired();
        builder.HasIndex(x => x.FileKey).IsUnique(); builder.HasIndex(x => x.AssetId);
        builder.ToTable("asset_documents", table => table.HasCheckConstraint("ck_asset_documents_size", "file_size_bytes IS NULL OR file_size_bytes >= 0"));
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.UploadedBy).OnDelete(DeleteBehavior.Restrict);
    }
}