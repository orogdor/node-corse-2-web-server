using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace HomeJoolink;

/// <summary>
/// Locate the VLC native files extracted by .NET's single-file bundler.
/// Search only application and runtime-authorized extraction directories.
/// </summary>
internal static class VlcLocator
{
    public static string? Find()
    {
        var roots = new List<string>();
        roots.Add(AppContext.BaseDirectory);
        if (Environment.ProcessPath is string exe)
            roots.Add(Path.GetDirectoryName(exe)!);
        if (AppContext.GetData("NATIVE_DLL_SEARCH_DIRECTORIES") is string native)
            roots.AddRange(native.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries));
        foreach (string root in roots.Distinct(StringComparer.OrdinalIgnoreCase))
        {
            foreach (string candidate in new[] {
                root,
                Path.Combine(root, "libvlc", "win-x64"),
                Path.Combine(root, "libvlc", "win-x64", "libvlc"),
                Path.Combine(root, "runtimes", "win-x64", "native")
            })
            {
                if (File.Exists(Path.Combine(candidate, "libvlc.dll")) &&
                    File.Exists(Path.Combine(candidate, "libvlccore.dll")))
                    return candidate;
            }
        }
        return null;
    }
}