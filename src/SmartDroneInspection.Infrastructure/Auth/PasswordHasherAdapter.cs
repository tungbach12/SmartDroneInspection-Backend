using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Auth;

/// <summary>Thin adapter over ASP.NET Core PasswordHasher (PBKDF2, per-user salt).</summary>
public sealed class PasswordHasherAdapter(
    Microsoft.AspNetCore.Identity.PasswordHasher<User> hasher) : IPasswordHasher
{
    public string HashPassword(string password) =>
        hasher.HashPassword(new User(), password);

    public PasswordVerification VerifyPassword(string passwordHash, string providedPassword) =>
        hasher.VerifyHashedPassword(new User(), passwordHash, providedPassword) switch
        {
            Microsoft.AspNetCore.Identity.PasswordVerificationResult.Success =>
                PasswordVerification.Success,
            Microsoft.AspNetCore.Identity.PasswordVerificationResult.SuccessRehashNeeded =>
                PasswordVerification.SuccessRehashNeeded,
            _ => PasswordVerification.Failed,
        };
}
