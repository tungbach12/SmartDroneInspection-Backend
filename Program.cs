using System;
using System.Linq;
using Microsoft.EntityFrameworkCore;
using Clean.Architecture.Infrastructure.Data;

var opts = new DbContextOptionsBuilder<AppDbContext>()
    .UseNpgsql("Host=localhost;Port=5432;Database=smartdroneinspection;Username=postgres;Password=local_dev_only_change_me")
    .Options;
try
{
    using var ctx = new AppDbContext(opts);
    var model = ctx.Model;
    Console.WriteLine("MODEL OK, entity types: " + model.GetEntityTypes().Count());
}
catch (Exception ex)
{
    var root = ex; while (root.InnerException != null) root = root.InnerException;
    Console.WriteLine("FAIL: " + root.Message);
}
