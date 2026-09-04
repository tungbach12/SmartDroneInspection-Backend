namespace MinimalClean.Architecture.Web.Domain.Interfaces;

public interface ITokenService
{
    (string Token, string JwtId, DateTime ExpiresAtUtc) CreateAccessToken(
        Guid userId, string email, string role);

    (string RawToken, string Hash) CreateRefreshToken();

    string HashToken(string rawToken);
}
