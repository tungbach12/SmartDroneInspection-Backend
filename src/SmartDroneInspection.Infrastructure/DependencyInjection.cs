using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Pgvector.EntityFrameworkCore;
using SmartDroneInspection.Infrastructure.Persistence;

namespace SmartDroneInspection.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        services.AddDbContext<ApplicationDbContext>(options =>
            options.UseNpgsql(
                configuration.GetConnectionString("DefaultConnection"),
                npgsql => npgsql.UseVector())
            .UseSnakeCaseNamingConvention());

        // TODO: register JWT auth, MinIO (IObjectStorage), SmartDroneHub typed HttpClient,
        // AI service clients as modules are implemented.

        return services;
    }
}
