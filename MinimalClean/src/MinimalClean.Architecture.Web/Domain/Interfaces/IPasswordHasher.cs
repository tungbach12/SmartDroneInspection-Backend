namespace MinimalClean.Architecture.Web.Domain.Interfaces;

public enum PasswordVerification
{
    Success,
    SuccessRehashNeeded,
    Failed,
}

public interface IPasswordHasher
{
    string HashPassword(string password);
    PasswordVerification VerifyPassword(string passwordHash, string providedPassword);
}
