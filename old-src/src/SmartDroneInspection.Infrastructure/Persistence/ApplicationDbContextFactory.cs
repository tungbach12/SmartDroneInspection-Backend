using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;
using Microsoft.Extensions.Configuration;
using Pgvector.EntityFrameworkCore;
using SmartDroneInspection.Application.Common.Interfaces;

namespace SmartDroneInspection.Infrastructure.Persistence;

/// <summary>Design-time factory for `dotnet ef migrations`.</summary>
public class ApplicationDbContextFactory : IDesignTimeDbContextFactory<ApplicationDbContext>
{
    public ApplicationDbContext CreateDbContext(string[] args)
    {
        var configuration = new ConfigurationBuilder()
            .SetBasePath(Path.Combine(Directory.GetCurrentDirectory(), "..", "SmartDroneInspection.Api"))
            .AddJsonFile("appsettings.json", optional: false)
            .AddJsonFile("appsettings.Development.json", optional: true)
            .AddEnvironmentVariables()
            .Build();

        var optionsBuilder = new DbContextOptionsBuilder<ApplicationDbContext>();
        optionsBuilder.UseNpgsql(configuration.GetConnectionString("DefaultConnection"), npgsql =>
            npgsql.UseVector())
            .UseSnakeCaseNamingConvention();

        return new ApplicationDbContext(optionsBuilder.Options, new DesignTimeCurrentUser());
    }

    /// <summary>No HTTP context at design time — audit stamps stay null, which is correct for migrations.</summary>
    private sealed class DesignTimeCurrentUser : ICurrentUserService
    {
        public Guid? UserId => null;
        public string? UserName => "design-time";
        public IReadOnlyList<string> Roles => [];
        public bool IsInRole(string role) => true;
        public string? ClientIp => null;
        public string? UserAgent => null;
    }
}
