namespace SmartDroneInspection.Application.Common.Interfaces;

/// <summary>
/// Token issuance abstraction implemented by Infrastructure (JWT + opaque refresh).
/// Handlers use it for login/refresh rotation; validation stays in JwtBearer middleware.
/// </summary>
public interface ITokenService
{
    /// <summary>Creates a signed access token. Returns (token, jwtId, expiresAtUtc).</summary>
    (string Token, string JwtId, DateTime ExpiresAtUtc) CreateAccessToken(Guid userId, string email, string role);

    /// <summary>Creates a random refresh token. Returns (rawToken, sha256Hash). Persist only the hash.</summary>
    (string RawToken, string Hash) CreateRefreshToken();

    /// <summary>SHA-256 hash of a raw refresh token, matching <c>refresh_tokens.token_hash</c>.</summary>
    string HashToken(string rawToken);
}
