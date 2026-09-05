using Ardalis.SmartEnum;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace Clean.Architecture.Infrastructure.Data.Config;

/// <summary>
/// Creates a string converter for an Ardalis.SmartEnum type.
/// EF Core 10's selector-based HasConversion&lt;string&gt;() no longer resolves converters
/// for SmartEnum (a reference type, not a CLR enum), so configs must use explicit converters.
/// </summary>
public static class SmartEnumStringConverter
{
    public static ValueConverter<TEnum, string> Create<TEnum>() where TEnum : SmartEnum<TEnum>
        => new(
            v => v.Name,
            s => SmartEnum<TEnum>.FromName(s, false));

    public static ValueConverter<TEnum?, string?> CreateNullable<TEnum>() where TEnum : SmartEnum<TEnum>
        => new(
            v => v == null ? null : v.Name,
            s => s == null ? null : SmartEnum<TEnum>.FromName(s, false));
}
