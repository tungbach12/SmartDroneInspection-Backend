using Ardalis.GuardClauses;
using MinimalClean.Architecture.Web.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;

namespace MinimalClean.Architecture.Web.Infrastructure;

public static class InfrastructureServiceExtensions
{
    public static IServiceCollection AddInfrastructureServices(
        this IServiceCollection services,
        ConfigurationManager config,
        ILogger logger)
    {
        string? connectionString = config.GetConnectionString("DefaultConnection") ?? config.GetConnectionString("AppDb");
        Guard.Against.Null(connectionString, "DefaultConnection is required.");

        services.AddScoped<EventDispatchInterceptor>();
        services.AddScoped<IDomainEventDispatcher, MediatorDomainEventDispatcher>();

        services.AddDbContext<AppDbContext>((provider, options) =>
        {
            var interceptor = provider.GetRequiredService<EventDispatchInterceptor>();
            options.UseNpgsql(connectionString, o => o.UseVector());
            options.UseSnakeCaseNamingConvention();
            options.AddInterceptors(interceptor);
        });

        services.AddScoped(typeof(IRepository<>), typeof(EfRepository<>))
               .AddScoped(typeof(IReadRepository<>), typeof(EfRepository<>));

        logger.LogInformation("{Project} services registered", "Infrastructure");

        return services;
    }
}
