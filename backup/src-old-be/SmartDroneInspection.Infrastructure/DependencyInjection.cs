using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.IdentityModel.Tokens;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Auth;
using SmartDroneInspection.Infrastructure.Persistence;
using SmartDroneInspection.Infrastructure.Persistence.Seed;

namespace SmartDroneInspection.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        services.AddDbContext<ApplicationDbContext>((sp, options) =>
        {
            options.UseNpgsql(
                configuration.GetConnectionString("DefaultConnection"),
                npgsql => npgsql.UseVector())
            .UseSnakeCaseNamingConvention();
        });
        services.AddScoped<IApplicationDbContext>(sp => sp.GetRequiredService<ApplicationDbContext>());

        services.AddHttpContextAccessor();
        services.AddScoped<ICurrentUserService, CurrentUserService>();

        services.AddOptions<JwtOptions>()
            .Bind(configuration.GetSection(JwtOptions.SectionName))
            .ValidateDataAnnotations()
            .ValidateOnStart();

        services.AddSingleton<Microsoft.AspNetCore.Identity.PasswordHasher<User>>();
        services.AddSingleton<ITokenService, JwtTokenService>();
        services.AddSingleton<IPasswordHasher, PasswordHasherAdapter>();
        services.AddScoped<AdminUserSeed>();

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(options =>
            {
                options.SaveToken = true;
                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidateAudience = true,
                    ValidateLifetime = true,
                    ValidateIssuerSigningKey = true,
                    ClockSkew = TimeSpan.FromSeconds(30),
                    NameClaimType = System.Security.Claims.ClaimTypes.Name,
                    RoleClaimType = System.Security.Claims.ClaimTypes.Role,
                };
            });

        // Consume the already-validated JwtOptions for the bearer's issuer/audience/key
        // instead of re-reading the raw config section inside the lambda above.
        services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
            .Configure<JwtOptions>((options, jwt) =>
            {
                options.RequireHttpsMetadata = !jwt.AllowInsecure;
                options.TokenValidationParameters!.ValidAlgorithms = [SecurityAlgorithms.HmacSha256];
                options.TokenValidationParameters!.ValidIssuer = jwt.Issuer;
                options.TokenValidationParameters!.ValidAudience = jwt.Audience;
                options.TokenValidationParameters!.IssuerSigningKey =
                    new SymmetricSecurityKey(Convert.FromBase64String(jwt.Key));
            });

        return services;
    }
}
