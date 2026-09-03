using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Missions;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Missions;

public sealed class MissionImageConfiguration : IEntityTypeConfiguration<MissionImage>
{
    public void Configure(EntityTypeBuilder<MissionImage> builder)
    {
        builder.ConfigureBase("mission_images");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.MinioObjectKey).HasMaxLength(1024).IsRequired(); builder.Property(x => x.ThumbnailObjectKey).HasMaxLength(1024); builder.Property(x => x.MimeType).HasMaxLength(255).IsRequired();
        builder.HasIndex(x => x.MinioObjectKey).IsUnique(); builder.HasIndex(x => new { x.DroneMissionId, x.CapturedAt });
        builder.ToTable("mission_images", table => table.HasCheckConstraint("ck_mission_images_values", "file_size_bytes >= 0 AND (width_px IS NULL OR width_px > 0) AND (height_px IS NULL OR height_px > 0) AND ((latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))"));
        builder.HasOne<DroneMission>().WithMany().HasForeignKey(x => x.DroneMissionId).OnDelete(DeleteBehavior.Cascade);
    }
}