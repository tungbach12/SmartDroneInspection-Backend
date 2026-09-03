namespace SmartDroneInspection.Application.Common.Interfaces;

/// <summary>
/// Password hashing abstraction implemented by Infrastructure with
/// ASP.NET Core's PasswordHasher (PBKDF2). Application stays framework-free.
/// </summary>
public interface IPasswordHasher
{
    string HashPassword(string password);

    /// <summary>Returns success / rehash-needed / failed.</summary>
    PasswordVerification VerifyPassword(string passwordHash, string providedPassword);
}

public enum PasswordVerification
{
    Failed = 0,
    Success = 1,
    SuccessRehashNeeded = 2,
}
