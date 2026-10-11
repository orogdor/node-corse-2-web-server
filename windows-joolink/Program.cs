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
        if (args.Length == 2 && args[0] == "--cloud-schema-self-test")
        {
            WriteTest(args[1], () => {
                var s = new JoolinkCloudClient.Session("test-user", "dummy-private-token");
                for (int variant = 0; variant < JoolinkCloudClient.PublicAppKeys.Length; variant++)
                for (int mode = 0; mode < 2; mode++)
                {
                    var payload = JoolinkCloudClient.BuildCameraRequest(s, mode == 0, variant);
                    var json = System.Text.Json.JsonSerializer.Serialize(payload);
                    using var doc = System.Text.Json.JsonDocument.Parse(json);
                    var root = doc.RootElement;
                    string got = root.GetProperty("open_id_app_key").GetString()!;
                    string force = root.GetProperty("force_query_bind").GetString()!;
                    var h = root.GetProperty("header");
                    var seq = h.GetProperty("seqno").GetString()!;
                    if (got != JoolinkCloudClient.PublicAppKeys[variant] ||
                        force != (mode == 0 ? "Y" : "N") ||
                        h.GetProperty("user_id").GetString() != "test-user" ||
                        h.GetProperty("token").GetString() != "dummy-private-token" ||
                        h.GetProperty("client_version").GetString() != JoolinkCloudClient.VendorAppVersion ||
                        root.GetProperty("app_version").GetString() != JoolinkCloudClient.VendorAppVersion ||
                        seq.Length < 18 || !seq.All(char.IsDigit))
                        throw new InvalidOperationException("Cloud request schema validation failed.");
                }
                for (int model = 0; model < JoolinkCloudClient.RequestPhoneModels.Length; model++)
                {
                    var payload = JoolinkCloudClient.BuildCameraRequest(s, true, 0, model);
                    string expected = JoolinkCloudClient.RequestPhoneModels[model];
                    if (!object.Equals(payload["phone_model"], expected))
                        throw new InvalidOperationException("Phone model mismatch.");
                    var header = (System.Collections.Generic.Dictionary<string, object>)payload["header"];
                    if (!object.Equals(header["phone_model"], expected))
                        throw new InvalidOperationException("Header phone model mismatch.");
                }
                return "OK - six vendor-style payload schemas + version 5.3.8.2 + phone model variants valid";
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
                "HomeJoolink Windows v1.0.6 start error\n" +
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