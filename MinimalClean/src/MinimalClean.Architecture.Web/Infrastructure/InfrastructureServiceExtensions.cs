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

        services.Configure<MinimalClean.Architecture.Web.Infrastructure.Auth.JwtOptions>(
            config.GetSection(MinimalClean.Architecture.Web.Infrastructure.Auth.JwtOptions.SectionName));
        services.AddScoped<MinimalClean.Architecture.Web.Domain.Interfaces.IPasswordHasher, MinimalClean.Architecture.Web.Infrastructure.Auth.PasswordHasherAdapter>();
        services.AddScoped<MinimalClean.Architecture.Web.Domain.Interfaces.ITokenService, MinimalClean.Architecture.Web.Infrastructure.Auth.JwtTokenService>();

        logger.LogInformation("{Project} services registered", "Infrastructure");

        return services;
    }
}
