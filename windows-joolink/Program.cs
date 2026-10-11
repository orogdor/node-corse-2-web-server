using System;
using System.IO;
using System.Windows.Forms;
using LibVLCSharp.Shared;

namespace HomeJoolink;
internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        if (args.Length == 2 && args[0] == "--self-test")
        {
            try
            {
                Core.Initialize(VlcLocator.Find());
                using var lib = new LibVLC("--no-video-title-show");
                File.WriteAllText(args[1], "OK - VLC initialized");
            }
            catch (Exception ex)
            {
                File.WriteAllText(args[1], "FAILED - " + ex.ToString());
                Environment.ExitCode = 1;
            }
            return;
        }
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}