using System.ComponentModel.DataAnnotations;

namespace MinimalClean.Architecture.Web.Infrastructure.Auth;

public sealed class JwtOptions
{
    public const string SectionName = "Jwt";

    [Required]
    public string Issuer { get; set; } = "SmartDroneInspection";

    [Required]
    public string Audience { get; set; } = "SmartDroneInspection.Client";

    /// <summary>Base64-encoded 256-bit symmetric key for HMAC-SHA256.</summary>
    [Required]
    public string Key { get; set; } = "c21hcnQtZHJvbmUtaW5zcGVjdGlvbi1zZWNyZXQta2V5LTIwMjY=";

    [Range(1, 1440)]
    public int AccessTokenMinutes { get; set; } = 60;

    [Range(1, 90)]
    public int RefreshTokenDays { get; set; } = 7;
}
