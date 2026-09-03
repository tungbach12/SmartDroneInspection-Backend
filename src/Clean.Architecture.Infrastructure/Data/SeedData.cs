namespace Clean.Architecture.Infrastructure.Data;

public static class SeedData
{
    public static async Task InitializeAsync(AppDbContext dbContext)
    {
        // SmartDrone seed is handled via AdminUserSeed in SmartDrone's original, keep minimal
        if (await dbContext.Organizations.AnyAsync() || await dbContext.Assets.AnyAsync())
        {
            return;
        }

        await Task.CompletedTask;
    }

    public static async Task PopulateTestDataAsync(AppDbContext dbContext)
    {
        await InitializeAsync(dbContext);
    }
}
