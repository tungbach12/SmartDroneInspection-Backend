using System.Reflection;
using NetArchTest.Rules;
using SmartDroneInspection.Domain.Common;
using Xunit;

namespace SmartDroneInspection.ArchitectureTests;

/// <summary>
/// Enforces the dependency direction of the Clean Architecture layout described in
/// docs/architecture/01-ARCHITECTURE-OVERVIEW.md. These tests fail the build if any
/// layer pulls an illegal reference (Domain referencing EF Core, Application
/// referencing Infrastructure, etc.) so the rule cannot be violated silently.
/// </summary>
public sealed class LayerDependenciesTests
{
    private const string DomainNamespace = "SmartDroneInspection.Domain";
    private const string ApplicationNamespace = "SmartDroneInspection.Application";
    private const string InfrastructureNamespace = "SmartDroneInspection.Infrastructure";
    private const string ApiNamespace = "SmartDroneInspection.Api";

    [Fact]
    public void Domain_Should_Not_Depend_On_Any_Other_Layer()
    {
        var result = Types.InAssembly(typeof(BaseEntity).Assembly)
            .ShouldNot()
            .HaveDependencyOnAny(
                ApplicationNamespace,
                InfrastructureNamespace,
                ApiNamespace)
            .GetResult();

        Assert.True(result.IsSuccessful,
            $"Domain must not reference any other layer. Violations:{Environment.NewLine}{FormatFailures(result)}");
    }

    [Fact]
    public void Domain_Should_Not_Reference_Frameworks()
    {
        // Domain must stay pure C# so the business rules survive infrastructure churn.
        // We forbid EF Core, ASP.NET, Npgsql, MediatR, FluentValidation, and MinIO.
        var result = Types.InAssembly(typeof(BaseEntity).Assembly)
            .ShouldNot()
            .HaveDependencyOnAny(
                "Microsoft.EntityFrameworkCore",
                "Microsoft.AspNetCore",
                "Npgsql",
                "MediatR",
                "FluentValidation",
                "Minio",
                "Pgvector")
            .GetResult();

        Assert.True(result.IsSuccessful,
            $"Domain must not reference framework packages. Violations:{Environment.NewLine}{FormatFailures(result)}");
    }

    [Fact]
    public void Application_Should_Not_Depend_On_Infrastructure_Or_Api()
    {
        var result = Types.InAssembly(typeof(Application.Common.Models.PagedResult<>).Assembly)
            .ShouldNot()
            .HaveDependencyOnAny(InfrastructureNamespace, ApiNamespace)
            .GetResult();

        Assert.True(result.IsSuccessful,
            $"Application must not reference Infrastructure or Api. Violations:{Environment.NewLine}{FormatFailures(result)}");
    }

    [Fact]
    public void Application_Should_Not_Reference_Infrastructure_Packages_Directly()
    {
        // Application talks to EF Core only through IApplicationDbContext (DbSet) so it
        // can use the abstractions, but concrete types from Npgsql/EF Core config live
        // in Infrastructure.
        var result = Types.InAssembly(typeof(Application.Common.Models.PagedResult<>).Assembly)
            .ShouldNot()
            .HaveDependencyOnAny("Npgsql", "Minio", "Serilog")
            .GetResult();

        Assert.True(result.IsSuccessful,
            $"Application must not reference infrastructure-only packages. Violations:{Environment.NewLine}{FormatFailures(result)}");
    }

    [Fact]
    public void Infrastructure_Should_Not_Depend_On_Api()
    {
        var result = Types.InAssembly(typeof(Infrastructure.Persistence.ApplicationDbContext).Assembly)
            .ShouldNot()
            .HaveDependencyOn(ApiNamespace)
            .GetResult();

        Assert.True(result.IsSuccessful,
            $"Infrastructure must not reference Api. Violations:{Environment.NewLine}{FormatFailures(result)}");
    }

    [Fact]
    public void Controllers_Should_Only_Depend_On_ISender_Or_HttpContext_Types()
    {
        // Thin controllers: only MediatR ISender and the ASP.NET MVC primitives they
        // need. They must never directly consume ApplicationDbContext, repositories,
        // or domain services.
        var apiAssembly = Assembly.Load("SmartDroneInspection.Api");
        var controllerTypes = Types.InAssembly(apiAssembly)
            .That()
            .ResideInNamespace(ApiNamespace)
            .And()
            .Inherit(typeof(Microsoft.AspNetCore.Mvc.ControllerBase))
            .GetTypes();

        var forbidden = new[]
        {
            "Microsoft.EntityFrameworkCore.DbContext",
            "SmartDroneInspection.Infrastructure.Persistence.ApplicationDbContext",
            "Microsoft.EntityFrameworkCore.DbSet",
        };

        var violations = new List<string>();
        foreach (var controller in controllerTypes)
        {
            var ctor = controller.GetConstructors().FirstOrDefault();
            if (ctor is null)
            {
                continue;
            }

            foreach (var parameter in ctor.GetParameters())
            {
                foreach (var forbiddenType in forbidden)
                {
                    if (parameter.ParameterType.FullName == forbiddenType ||
                        parameter.ParameterType.BaseType?.FullName == forbiddenType ||
                        parameter.ParameterType.GetInterfaces().Any(i => i.FullName == forbiddenType))
                    {
                        violations.Add(
                            $"{controller.FullName} ctor parameter {parameter.Name} : {parameter.ParameterType.FullName} (forbidden: {forbiddenType})");
                    }
                }
            }
        }

        Assert.True(violations.Count == 0,
            "Controllers must not inject DbContext, ApplicationDbContext, or DbSet. Violations:" +
            Environment.NewLine + string.Join(Environment.NewLine, violations));
    }

    [Fact]
    public void MediatR_Handlers_Should_Only_Live_In_Application_Layer()
    {
        // The mediator pipeline must dispatch to handlers that live in Application; if a
        // handler leaks into Infrastructure or Api the boundary has been broken.
        var assembly = typeof(BaseEntity).Assembly;
        var result = Types.InAssembly(assembly)
            .That()
            .ImplementInterface(typeof(MediatR.IRequestHandler<,>))
            .Should()
            .ResideInNamespace(ApplicationNamespace)
            .GetResult();

        Assert.True(result.IsSuccessful,
            "IRequestHandler implementations must live in SmartDroneInspection.Application. Violations:" +
            Environment.NewLine + FormatFailures(result));
    }

    [Fact]
    public void Domain_Entities_Should_Not_Expose_Public_Setters_For_Collection_Navigation()
    {
        // Collection navigation properties must be read-only; otherwise EF Core can
        // silently swap the backing collection and break invariants.
        var entityTypes = Types.InAssembly(typeof(BaseEntity).Assembly)
            .That()
            .Inherit(typeof(BaseEntity))
            .GetTypes();

        var violations = new List<string>();
        foreach (var entity in entityTypes)
        {
            foreach (var prop in entity.GetProperties(BindingFlags.Public | BindingFlags.Instance))
            {
                if (!prop.CanWrite)
                {
                    continue;
                }

                var setter = prop.GetSetMethod(nonPublic: false);
                if (setter is null)
                {
                    continue;
                }

                if (typeof(System.Collections.IEnumerable).IsAssignableFrom(prop.PropertyType) &&
                    prop.PropertyType != typeof(string))
                {
                    violations.Add($"{entity.FullName}.{prop.Name} exposes a public setter on a collection navigation.");
                }
            }
        }

        Assert.True(violations.Count == 0,
            "Domain collection navigations must not expose public setters. Violations:" +
            Environment.NewLine + string.Join(Environment.NewLine, violations));
    }

    private static string FormatFailures(TestResult result)
    {
        if (result.IsSuccessful)
        {
            return string.Empty;
        }

        return string.Join(Environment.NewLine, result.FailingTypeNames ?? Array.Empty<string>());
    }
}
