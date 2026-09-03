using FluentValidation;
using Microsoft.AspNetCore.Diagnostics;

namespace SmartDroneInspection.Api.Middleware;

/// <summary>Maps known exceptions to ProblemDetails. Registered via AddExceptionHandler.</summary>
public sealed class GlobalExceptionHandler(ILogger<GlobalExceptionHandler> logger) : IExceptionHandler
{
    public async ValueTask<bool> TryHandleAsync(HttpContext context, Exception exception, CancellationToken cancellationToken)
    {
        var (status, title) = exception switch
        {
            ValidationException v => (StatusCodes.Status400BadRequest,
                string.Join("; ", v.Errors.Select(e => e.ErrorMessage))),
            UnauthorizedAccessException => (StatusCodes.Status403Forbidden, "Forbidden"),
            KeyNotFoundException knf => (StatusCodes.Status404NotFound, knf.Message),
            InvalidOperationException ioe => (StatusCodes.Status409Conflict, ioe.Message),
            _ => (StatusCodes.Status500InternalServerError, "An unexpected error occurred.")
        };

        if (status == StatusCodes.Status500InternalServerError)
        {
            logger.LogError(exception, "Unhandled exception on {Path}", context.Request.Path);
        }

        await Results.Problem(title: title, statusCode: status)
            .ExecuteAsync(context);

        return true;
    }
}
