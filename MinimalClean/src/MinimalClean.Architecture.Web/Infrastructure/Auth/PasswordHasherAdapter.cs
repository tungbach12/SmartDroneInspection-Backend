using Microsoft.AspNetCore.Identity;
using MinimalClean.Architecture.Web.Domain.Interfaces;
using MinimalClean.Architecture.Web.Domain.Users;

namespace MinimalClean.Architecture.Web.Infrastructure.Auth;

public sealed class PasswordHasherAdapter : IPasswordHasher
{
    private readonly PasswordHasher<User> _hasher = new();

    public string HashPassword(string password) =>
        _hasher.HashPassword(new User(), password);

    public PasswordVerification VerifyPassword(string passwordHash, string providedPassword) =>
        _hasher.VerifyHashedPassword(new User(), passwordHash, providedPassword) switch
        {
            PasswordVerificationResult.Success => PasswordVerification.Success,
            PasswordVerificationResult.SuccessRehashNeeded => PasswordVerification.SuccessRehashNeeded,
            _ => PasswordVerification.Failed,
        };
}
