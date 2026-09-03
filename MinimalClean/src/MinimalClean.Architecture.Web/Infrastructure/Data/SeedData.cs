using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace MinimalClean.Architecture.Web.Infrastructure.Data;

public static class SeedData
{
    public static async Task InitializeAsync(AppDbContext dbContext, ILogger logger)
    {
        // Keep for SmartDrone — seed only if empty
        if (await dbContext.Organizations.AnyAsync() || await dbContext.Assets.AnyAsync())
        {
            logger.LogInformation("DB has SmartDrone data - seeding not required.");
            return;
        }

        logger.LogInformation("Seeding SmartDrone minimal data skipped (use AdminUserSeed).");
        await Task.CompletedTask;
    }

    public static async Task PopulateTestDataAsync(AppDbContext dbContext, ILogger logger)
    {
        await InitializeAsync(dbContext, logger);
    }
}
