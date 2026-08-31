using System.Diagnostics;
using MediatR;
using Microsoft.Extensions.Logging;

namespace SmartDroneInspection.Application.Common.Behaviors;

/// <summary>Logs every MediatR request with timing. Registered before ValidationBehavior.</summary>
public sealed class LoggingBehavior<TRequest, TResponse>(ILogger<LoggingBehavior<TRequest, TResponse>> logger)
    : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
{
    public async Task<TResponse> Handle(
        TRequest request,
        RequestHandlerDelegate<TResponse> next,
        CancellationToken ct)
    {
        var name = typeof(TRequest).Name;
        var sw = Stopwatch.StartNew();
        try
        {
            var response = await next();
            logger.LogInformation("{Request} handled in {Elapsed}ms", name, sw.ElapsedMilliseconds);
            return response;
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "{Request} failed in {Elapsed}ms", name, sw.ElapsedMilliseconds);
            throw;
        }
    }
}
