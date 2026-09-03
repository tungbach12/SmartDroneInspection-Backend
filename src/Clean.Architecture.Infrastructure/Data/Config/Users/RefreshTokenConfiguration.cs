using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;

namespace Clean.Architecture.Infrastructure.Data.Config.Users;

public sealed class RefreshTokenConfiguration : IEntityTypeConfiguration<RefreshToken>
{
    public void Configure(EntityTypeBuilder<RefreshToken> builder)
    {
        builder.ConfigureBase("refresh_tokens");
        builder.Property(x => x.Id).HasVogenConversion();
        builder.Property(x => x.TokenHash).HasMaxLength(128).IsRequired();
        builder.Property(x => x.JwtId).HasMaxLength(128).IsRequired();
        builder.Property(x => x.RevokedReason).HasMaxLength(500);
        builder.Property(x => x.UserAgent).HasMaxLength(1000);
        builder.Property(x => x.IpAddress).HasMaxLength(45);
        builder.HasIndex(x => x.TokenHash).IsUnique();
        builder.HasIndex(x => x.JwtId).IsUnique();

        // Bind the `User` navigation property to the `UserId` FK (avoids EF creating
        // a shadow `user_id1` property for the navigation).
        builder.HasOne(x => x.User)
            .WithMany()
            .HasForeignKey(x => x.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Self-reference for token rotation chain
        builder.HasOne<RefreshToken>()
            .WithMany()
            .HasForeignKey(x => x.ReplacedByTokenId)
            .OnDelete(DeleteBehavior.SetNull);

        builder.ToTable("refresh_tokens", table => table.HasCheckConstraint("ck_refresh_tokens_expiry", "expires_at > created_at"));
    }
}