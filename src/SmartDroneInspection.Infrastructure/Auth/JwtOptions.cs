using System.ComponentModel.DataAnnotations;

namespace SmartDroneInspection.Infrastructure.Auth;

public sealed class JwtOptions
{
    public const string SectionName = "Jwt";

    [Required, MinLength(32)]
    public string Key { get; init; } = "";

    [Required]
    public string Issuer { get; init; } = "";

    [Required]
    public string Audience { get; init; } = "";

    public int AccessTokenMinutes { get; init; } = 15;

    public bool AllowInsecure { get; init; }
}
