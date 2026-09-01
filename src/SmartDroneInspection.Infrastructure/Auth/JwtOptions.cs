using System.ComponentModel.DataAnnotations;

namespace SmartDroneInspection.Infrastructure.Auth;

public sealed class JwtOptions : IValidatableObject
{
    public const string SectionName = "Jwt";

    /// <summary>
    /// Base64-encoded signing key. RFC 7518 §3.4 requires ≥ 256 bits (32 bytes)
    /// of key material for HS256 — 32 base64 characters decode to only 24 bytes
    /// (192 bits), which fails at token-signing time with IDX10720.
    /// </summary>
    [Required]
    public string Key { get; init; } = "";

    [Required]
    public string Issuer { get; init; } = "";

    [Required]
    public string Audience { get; init; } = "";

    public int AccessTokenMinutes { get; init; } = 15;

    public bool AllowInsecure { get; init; }

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        byte[] keyBytes;
        var decodeFailed = false;
        try
        {
            keyBytes = Convert.FromBase64String(Key);
        }
        catch (FormatException)
        {
            decodeFailed = true;
            keyBytes = [];
        }

        if (decodeFailed)
        {
            yield return new ValidationResult(
                "Jwt:Key must be a valid Base64 string.", [nameof(Key)]);
        }
        else if (keyBytes.Length < 32)
        {
            yield return new ValidationResult(
                $"Jwt:Key must decode to at least 256 bits (32 bytes) for HS256; got {keyBytes.Length * 8} bits.",
                [nameof(Key)]);
        }
    }
}
