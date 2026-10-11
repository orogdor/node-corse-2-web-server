using System;
using System.IO;
using System.Linq;
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
            WriteTest(args[1], () => {
                Core.Initialize(VlcLocator.Find());
                using var lib = new LibVLC("--no-video-title-show");
                return "OK - VLC initialized";
            });
            return;
        }
        ApplicationConfiguration.Initialize();
        if (args.Length == 2 && args[0] == "--ui-self-test")
        {
            WriteTest(args[1], () => {
                using var f = new MainForm();
                f.Show();
                Application.DoEvents();
                var split = f.Controls.OfType<SplitContainer>().Single();
                if (!f.Visible || !f.IsHandleCreated ||
                    split.Panel1.Width < 200 || split.Panel2.Width < 200)
                    throw new InvalidOperationException(
                        $"UI invisible or too small: window {f.Width}x{f.Height}, " +
                        $"panels {split.Panel1.Width}x{split.Panel2.Width}");
                string details = "OK - UI window constructed and shown, " +
                                 $"panels={split.Panel1.Width}/{split.Panel2.Width}";
                f.Close();
                return details;
            });
            return;
        }
        try
        {
            Application.ThreadException += (_, e) => ReportCrash(e.Exception);
            AppDomain.CurrentDomain.UnhandledException += (_, e) => {
                if (e.ExceptionObject is Exception ex) SaveCrash(ex);
            };
            Application.Run(new MainForm());
        }
        catch (Exception ex) { ReportCrash(ex); Environment.ExitCode = 1; }
    }
    private static void WriteTest(string path, Func<string> test)
    {
        try { File.WriteAllText(path, test()); }
        catch (Exception ex)
        {
            try { File.WriteAllText(path, "FAILED - " + ex.ToString()); } catch { }
            Environment.ExitCode = 1;
        }
    }
    private static string SaveCrash(Exception ex)
    {
        try
        {
            string dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "HomeJoolink");
            Directory.CreateDirectory(dir);
            string path = Path.Combine(dir, "last-startup-error.txt");
            File.WriteAllText(path,
                "HomeJoolink Windows v1.0.3 start error\n" +
                DateTimeOffset.Now.ToString("u") + "\n" + ex.ToString());
            return path;
        }
        catch { return "Logifaili salvestamine ebaõnnestus."; }
    }
    private static void ReportCrash(Exception ex)
    {
        string path = SaveCrash(ex);
        try
        {
            MessageBox.Show(
                "HomeJoolinki käivitamisel tekkis viga.\n" +
                ex.GetType().Name + ": " + ex.Message +
                "\n\nDiagnostika: " + path,
                "HomeJoolink – käivitusviga", MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }
        catch { }
    }
}