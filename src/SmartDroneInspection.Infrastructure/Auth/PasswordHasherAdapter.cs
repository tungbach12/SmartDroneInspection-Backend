using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Auth;

/// <summary>Thin adapter over ASP.NET Core PasswordHasher (PBKDF2, per-user salt).</summary>
public sealed class PasswordHasherAdapter : IPasswordHasher
{
    private readonly Microsoft.AspNetCore.Identity.PasswordHasher<User> _hasher = new();

    public string HashPassword(string password) =>
        _hasher.HashPassword(new User(), password);

    public PasswordVerification VerifyPassword(string passwordHash, string providedPassword) =>
        _hasher.VerifyHashedPassword(new User(), passwordHash, providedPassword) switch
        {
            Microsoft.AspNetCore.Identity.PasswordVerificationResult.Success =>
                PasswordVerification.Success,
            Microsoft.AspNetCore.Identity.PasswordVerificationResult.SuccessRehashNeeded =>
                PasswordVerification.SuccessRehashNeeded,
            _ => PasswordVerification.Failed,
        };
}
