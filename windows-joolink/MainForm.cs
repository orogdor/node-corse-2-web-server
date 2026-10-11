using System.Drawing;
using System.Windows.Forms;
using LibVLCSharp.Shared;
using LibVLCSharp.WinForms;

namespace HomeJoolink;

public sealed class MainForm : Form
{
    private static readonly Color BG = Color.FromArgb(18, 24, 35);
    private static readonly Color PANEL = Color.FromArgb(29, 38, 52);
    private static readonly Color FIELD = Color.FromArgb(42, 54, 71);
    private static readonly Color TXT = Color.FromArgb(234, 242, 248);
    private static readonly Color SUB = Color.FromArgb(167, 186, 201);
    private readonly JoolinkCloudClient cloud = new();
    private readonly CancellationTokenSource closing = new();
    private JoolinkCloudClient.Session? session;
    private readonly TextBox account = new(), password = new(), url = new(), log = new();
    private readonly ComboBox decoder = new(), transport = new(), requestMode = new(), appKey = new(), phoneModel = new();
    private readonly ListBox cameras = new();
    private readonly Label cloudStatus = new(), videoStatus = new();
    private readonly Button login = new(), refresh = new();
    private readonly Panel videoPanel = new();
    private LibVLC? lib;
    private MediaPlayer? player;
    private VideoView? view;

    public MainForm()
    {
        Text = "HomeJoolink — Windows v1.0.6 TEST";
        AutoScaleMode = AutoScaleMode.Dpi;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = BG; ForeColor = TXT;
        Font = new Font("Segoe UI", 10f);
        // Fit the actual Windows work area, including taskbar and DPI scaling.
        var area = Screen.PrimaryScreen?.WorkingArea ?? SystemInformation.WorkingArea;
        MinimumSize = new Size(Math.Min(760, area.Width), Math.Min(510, area.Height));
        Size = new Size(Math.Min(1210, area.Width), Math.Min(790, area.Height));
        // Let WinForms center the window on the primary display. This also works
        // when an external monitor used previously has been disconnected.
        MakeUi();
        Shown += (_, _) => {
            FitToWorkingArea();
            // Video is optional and should not prevent the main window from showing.
            BeginInvoke(new Action(InitPlayer));
        };
        FormClosing += (_, _) => closing.Cancel();
        FormClosed += (_, _) => Shutdown();
        WriteLog("Rakendus käivitus. Pilve otsevideo ei ole veel toetatud.");
    }

    private void FitToWorkingArea()
    {
        var area = Screen.FromControl(this).WorkingArea;
        if (Width > area.Width || Height > area.Height)
            Size = new Size(Math.Min(Width, area.Width), Math.Min(Height, area.Height));
        if (Left < area.Left || Top < area.Top || Right > area.Right || Bottom > area.Bottom)
            Location = new Point(area.Left + Math.Max(0, (area.Width - Width) / 2),
                                 area.Top + Math.Max(0, (area.Height - Height) / 2));
    }
    private static FlowLayoutPanel Flow(TabPage page)
    {
        var f = new FlowLayoutPanel { Dock = DockStyle.Fill, BackColor = PANEL,
            AutoScroll = true, FlowDirection = FlowDirection.TopDown, WrapContents = false,
            Padding = new Padding(12, 18, 12, 16) };
        // Always size inputs and labels to the visible page. No horizontal overflow
        // when Windows uses 125%, 150%, or 200% scaling.
        f.ClientSizeChanged += (_, _) => {
            int width = Math.Max(100, f.ClientSize.Width - f.Padding.Horizontal - 22);
            foreach (Control control in f.Controls)
            {
                if (control.Width != width) control.Width = width;
                if (control is Label label && label.Tag is int baseHeight)
                {
                    int needed = TextRenderer.MeasureText(label.Text, label.Font,
                        new Size(width, int.MaxValue), TextFormatFlags.WordBreak).Height + 12;
                    label.Height = Math.Max(baseHeight, needed);
                }
            }
        };
        page.Controls.Add(f);
        return f;
    }
    private static Label TextLabel(string value, bool head = false, int height = 44)
    {
        return new Label { Text = value, ForeColor = head ? TXT : SUB,
            Font = new Font("Segoe UI", head ? 12 : 9.3f,
                head ? FontStyle.Bold : FontStyle.Regular),
            Width = 330, Height = height, Tag = height, Margin = new Padding(3, 3, 3, 8) };
    }
    private static void StyleText(TextBox b, string placeholder, bool secret = false)
    {
        b.Width = 330; b.Height = 38; b.BackColor = FIELD; b.ForeColor = TXT;
        b.BorderStyle = BorderStyle.FixedSingle; b.PlaceholderText = placeholder;
        b.UseSystemPasswordChar = secret; b.Margin = new Padding(3, 2, 3, 11);
    }
    private static Button StyledButton(string text, bool main = false)
    {
        var b = new Button { Text = text, Width = 330, Height = 40, FlatStyle = FlatStyle.Flat,
            ForeColor = TXT, BackColor = main ? Color.FromArgb(36, 135, 210) : FIELD,
            Margin = new Padding(3, 3, 3, 9), Cursor = Cursors.Hand };
        b.FlatAppearance.BorderSize = 0;
        return b;
    }
    private void MakeUi()
    {
        var split = new SplitContainer {
            Dock = DockStyle.Fill, Size = ClientSize,
            Panel1MinSize = 230, Panel2MinSize = 230
        };
        split.SplitterDistance = Math.Max(235, Math.Min(385, ClientSize.Width / 3));
        Controls.Add(split);
        var tabs = new TabControl { Dock = DockStyle.Fill };
        split.Panel1.Controls.Add(tabs);
        var tCloud = new TabPage("Pilvekonto") { BackColor = PANEL };
        var tVideo = new TabPage("Videovoog") { BackColor = PANEL };
        var tDiag = new TabPage("Diagnostika") { BackColor = PANEL };
        tabs.TabPages.AddRange(new[] { tCloud, tVideo, tDiag });
        SetupCloud(Flow(tCloud));
        SetupVideo(Flow(tVideo));
        SetupDiagnostics(tDiag);

        var frame = new TableLayoutPanel { Dock = DockStyle.Fill,
            RowCount = 3, ColumnCount = 1, BackColor = BG };
        frame.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        frame.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        frame.RowStyles.Add(new RowStyle(SizeType.Absolute, 60));
        split.Panel2.Controls.Add(frame);
        frame.Controls.Add(new Label { Dock = DockStyle.Fill,
            Text = "  HOMEJOOLINK   /   LIVE VIDEO", Font = new Font("Segoe UI Semibold", 15),
            ForeColor = TXT, BackColor = PANEL,
            TextAlign = ContentAlignment.MiddleLeft }, 0, 0);
        videoPanel.Dock = DockStyle.Fill; videoPanel.BackColor = Color.Black;
        frame.Controls.Add(videoPanel, 0, 1);
        videoPanel.Controls.Add(new Label { Dock = DockStyle.Fill,
            Text = "Videopilt pole avatud.\n\nSisesta videovoo RTSP / HTTP aadress.",
            ForeColor = SUB, BackColor = Color.Black,
            TextAlign = ContentAlignment.MiddleCenter, Font = new Font("Segoe UI", 13) });
        videoStatus.Dock = DockStyle.Fill; videoStatus.BackColor = PANEL;
        videoStatus.ForeColor = SUB; videoStatus.TextAlign = ContentAlignment.MiddleLeft;
        videoStatus.Padding = new Padding(14, 0, 8, 0);
        videoStatus.Text = "Ootel — Joolinki pilve otsevideo pole selles versioonis toetatud";
        frame.Controls.Add(videoStatus, 0, 2);
    }
    private void SetupCloud(FlowLayoutPanel panel)
    {
        panel.Controls.Add(TextLabel("Joolinki pilvekonto", true, 36));
        panel.Controls.Add(TextLabel("Otseühendus Joolinki HTTPS-serveriga. Parooli ega seansitokenit ei salvestata arvutisse.", height: 72));
        StyleText(account, "E-post või telefon");
        StyleText(password, "Joolinki parool", true);
        panel.Controls.Add(account); panel.Controls.Add(password);
        SetButton(login, "Logi Joolinki kontosse", true);
        login.Click += async (_, _) => await DoLogin();
        panel.Controls.Add(login);
        SetButton(refresh, "Värskenda kaameraloendit");
        refresh.Enabled = false; refresh.Click += async (_, _) => await LoadCameras();
        panel.Controls.Add(refresh);
        panel.Controls.Add(TextLabel("Kaameraloendi päringurežiim", true, 33));
        requestMode.DropDownStyle = ComboBoxStyle.DropDownList;
        requestMode.BackColor = FIELD; requestMode.ForeColor = TXT;
        requestMode.Width = 330;
        requestMode.Items.AddRange(new object[] {
            "Originaal: force_query_bind = Y",
            "Originaal: force_query_bind = N"
        });
        requestMode.SelectedIndex = 0;
        panel.Controls.Add(requestMode);
        panel.Controls.Add(TextLabel("Tootja rakenduse API võtme variant", true, 38));
        appKey.DropDownStyle = ComboBoxStyle.DropDownList;
        appKey.BackColor = FIELD; appKey.ForeColor = TXT; appKey.Width = 330;
        appKey.Items.AddRange(new object[] {
            "Joolink GP tavapärane",
            "Joolink GP alternatiiv 1",
            "Joolink GP alternatiiv 2"
        });
        appKey.SelectedIndex = 0;
        panel.Controls.Add(appKey);
        panel.Controls.Add(TextLabel("Kaamerapäringu seadmemudel (katse)", true, 45));
        phoneModel.DropDownStyle = ComboBoxStyle.DropDownList;
        phoneModel.BackColor = FIELD; phoneModel.ForeColor = TXT; phoneModel.Width = 330;
        phoneModel.Items.AddRange(new object[] { "Windows PC (senine)", "Samsung Galaxy S24 (SM-S921B)" });
        phoneModel.SelectedIndex = 0;
        panel.Controls.Add(phoneModel);
        panel.Controls.Add(TextLabel("Originaalse Androidi rakenduse versioon 5.3.8.2. Kui serveri viga jätkub, proovi telefonimudelit SM-S921B.", height: 85));
        var logout = StyledButton("Lõpeta seanss");
        logout.Click += (_, _) => { session = null; cameras.Items.Clear();
            refresh.Enabled = false; password.Clear(); cloudStatus.Text = "Seanss lõpetatud.";
            WriteLog("Seanss lõpetati."); };
        panel.Controls.Add(logout);
        cloudStatus.Width = 330; cloudStatus.Height = 58;
        cloudStatus.ForeColor = SUB; cloudStatus.Text = "Pole sisse logitud.";
        panel.Controls.Add(cloudStatus);
        panel.Controls.Add(TextLabel("Kontoga seotud kaamerad", true, 34));
        cameras.Size = new Size(327, 188); cameras.BackColor = FIELD;
        cameras.ForeColor = TXT; cameras.DisplayMember = "Name";
        cameras.SelectedIndexChanged += (_, _) => {
            if (cameras.SelectedItem is JoolinkCloudClient.CloudCamera cam)
                cloudStatus.Text = cam.Name + " | " + cam.Model + " | " + cam.OnlineStatus;
        };
        panel.Controls.Add(cameras);
        panel.Controls.Add(TextLabel("Pilvekonto kaameraloend ei anna veel videopilti. Videovoog peab olema eraldi RTSP/HTTP aadressiga.", height: 83));
    }
    private static void SetButton(Button b, string text, bool primary = false)
    {
        b.Text = text; b.Width = 330; b.Height = 40;
        b.FlatStyle = FlatStyle.Flat; b.FlatAppearance.BorderSize = 0;
        b.BackColor = primary ? Color.FromArgb(36, 135, 210) : FIELD;
        b.ForeColor = TXT; b.Margin = new Padding(3, 3, 3, 9);
    }
    private void SetupVideo(FlowLayoutPanel panel)
    {
        panel.Controls.Add(TextLabel("Videopleier", true, 38));
        panel.Controls.Add(TextLabel("RTSP, RTSPS või HTTP(S) videovoog. Pilvekaamera nimi või ID ei ole vooaadress.", height: 65));
        StyleText(url, "rtsp://192.168.1.20:554/live/ch00_0");
        panel.Controls.Add(url);
        panel.Controls.Add(TextLabel("Videodekooder", true, 34));
        decoder.DropDownStyle = ComboBoxStyle.DropDownList;
        decoder.Width = 330; decoder.BackColor = FIELD; decoder.ForeColor = TXT;
        decoder.Items.AddRange(new object[] { "Automaatne", "Tarkvaraline (CPU)", "Riistvaraline (D3D11VA)" });
        decoder.SelectedIndex = 0; panel.Controls.Add(decoder);
        panel.Controls.Add(TextLabel("RTSP transport", true, 34));
        transport.DropDownStyle = ComboBoxStyle.DropDownList; transport.Width = 330;
        transport.BackColor = FIELD; transport.ForeColor = TXT;
        transport.Items.AddRange(new object[] { "TCP", "UDP" });
        transport.SelectedIndex = 0; panel.Controls.Add(transport);
        var start = StyledButton("▶ Ava videopilt", true);
        start.Click += (_, _) => Play();
        panel.Controls.Add(start);
        var stop = StyledButton("■ Peata video");
        stop.Click += (_, _) => { player?.Stop(); videoStatus.Text = "Video peatatud."; WriteLog("Video peatati."); };
        panel.Controls.Add(stop);
        panel.Controls.Add(TextLabel("Musta või valede värvidega pildi korral proovi vahetada dekoodrit ning ava voog uuesti. Riistvararežiim ei pruugi iga graafikakaardiga töötada.", height: 105));
        panel.Controls.Add(TextLabel("Joolinki pilveserveri eraldi P2P/LinkVisual videoprotokoll pole veel integreeritud.", height: 80));
    }
    private void SetupDiagnostics(TabPage page)
    {
        var frame = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1,
            RowCount = 3, BackColor = PANEL, Padding = new Padding(10) };
        frame.RowStyles.Add(new RowStyle(SizeType.Absolute, 60));
        frame.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        frame.RowStyles.Add(new RowStyle(SizeType.Absolute, 55));
        page.Controls.Add(frame);
        frame.Controls.Add(TextLabel("Ühenduse diagnostika", true), 0, 0);
        log.Multiline = true; log.ScrollBars = ScrollBars.Vertical; log.ReadOnly = true;
        log.Dock = DockStyle.Fill; log.BackColor = FIELD; log.ForeColor = TXT;
        log.Font = new Font("Consolas", 9);
        frame.Controls.Add(log, 0, 1);
        var copy = StyledButton("Kopeeri diagnostika");
        copy.Dock = DockStyle.Fill;
        copy.Click += (_, _) => { if (log.Text.Length > 0) Clipboard.SetText(log.Text); };
        frame.Controls.Add(copy, 0, 2);
    }
    private async Task DoLogin()
    {
        string name = account.Text.Trim(), secret = password.Text;
        password.Clear(); login.Enabled = false;
        cloudStatus.Text = "Ühendan tootja HTTPS-serveriga …";
        WriteLog("Sisselogimispäring algas.");
        try
        {
            session = await cloud.LoginAsync(name, secret, closing.Token);
            if (IsDisposed) return;
            WriteLog("Sisselogimine õnnestus. Token ainult töömälu sees.");
            refresh.Enabled = true; await LoadCameras();
        }
        catch (OperationCanceledException) { if (!IsDisposed) cloudStatus.Text = "Katkestatud."; }
        catch (Exception ex) { cloudStatus.Text = "Sisselogimise viga: " + Safe(ex.Message);
            WriteLog("Sisselogimise viga: " + ex.GetType().Name + " — " + Safe(ex.Message)); }
        finally { if (!IsDisposed) login.Enabled = true; }
    }
    private async Task LoadCameras()
    {
        if (session == null) return;
        refresh.Enabled = false;
        try
        {
            var mode = requestMode.SelectedIndex == 0;
            int variant = Math.Max(0, appKey.SelectedIndex);
            int model = Math.Max(0, phoneModel.SelectedIndex);
            WriteLog("Kaameraloendi päring /v3/am/query_device_list; " +
                     (mode ? "force_query_bind=Y" : "force_query_bind=N") +
                     "; GP-võti " + (variant + 1) + "; V3-päis; open_id_app_key kaasas; versioon 5.3.8.2; phone_model=" +
                     JoolinkCloudClient.RequestPhoneModels[model]);
            var list = await cloud.CamerasAsync(session, mode, variant, model, closing.Token);
            if (IsDisposed) return;
            cameras.Items.Clear();
            foreach (var cam in list) cameras.Items.Add(cam);
            cloudStatus.Text = "Pilvekaameraid: " + list.Count;
            WriteLog("Kaameraloendi päring õnnestus; kogus: " + list.Count);
        }
        catch (OperationCanceledException) { if (!IsDisposed) cloudStatus.Text = "Katkestatud."; }
        catch (JoolinkCloudClient.DeviceListException ex)
        {
            cloudStatus.Text = "Serveri kood: " + ex.Code +
                ". Vaata diagnostikat ja proovi teist päringurežiimi.";
            WriteLog("Kaameraloendi vastus: " + ex.Code + ", režiim: " + ex.RequestMode);
            WriteLog("Tagastatud väljad (ainult väljade nimed): " + ex.ResponseFields);
            WriteLog("HTTP " + ex.HttpStatus + ". Server ei kinnitanud selle päringu õnnestumist.");
            if (ex.Code == "E_000_002")
                WriteLog("E_000_002: tootja APK teade on 'Connection failed, server error, please try again later'. See ei tõesta, kas probleem on päringus või serveris.");
        }
        catch (Exception ex) { cloudStatus.Text = "Kaameraloendi ühenduse viga. Vaata diagnostikat.";
            WriteLog("Kaameraloendi viga: " + ex.GetType().Name + " — " + Safe(ex.Message)); }
        finally { if (!IsDisposed && session != null) refresh.Enabled = true; }
    }
    private void InitPlayer()
    {
        try
        {
            Core.Initialize(VlcLocator.Find());
            lib = new LibVLC("--no-video-title-show");
            player = new MediaPlayer(lib);
            view = new VideoView { Dock = DockStyle.Fill, BackColor = Color.Black, MediaPlayer = player };
            videoPanel.Controls.Add(view); view.BringToFront();
            player.Opening += (_, _) => SetVideoStatus("Video avamine …");
            player.Playing += (_, _) => SetVideoStatus("Videovoog mängib");
            player.EncounteredError += (_, _) => { SetVideoStatus("Videopleieri viga — kontrolli aadressi."); SetLog("VLC: EncounteredError"); };
            player.Stopped += (_, _) => SetVideoStatus("Video peatatud.");
            WriteLog("LibVLC lähtestamine õnnestus.");
        }
        catch (Exception ex) { videoStatus.Text = "LibVLC teeke ei õnnestunud avada.";
            WriteLog("LibVLC viga: " + ex.GetType().Name); }
    }
    private void Play()
    {
        if (lib == null || player == null) { videoStatus.Text = "LibVLC ei ole saadaval."; return; }
        if (!Uri.TryCreate(url.Text.Trim(), UriKind.Absolute, out var uri) ||
            !new[] { "rtsp", "rtsps", "http", "https" }.Contains(uri.Scheme, StringComparer.OrdinalIgnoreCase))
        { videoStatus.Text = "Sisesta korrektne RTSP/HTTP(S) aadress."; return; }
        try
        {
            player.Stop();
            using var media = new Media(lib, uri);
            media.AddOption(":network-caching=750");
            if (transport.SelectedIndex == 0) media.AddOption(":rtsp-tcp");
            if (decoder.SelectedIndex == 1) media.AddOption(":avcodec-hw=none");
            if (decoder.SelectedIndex == 2) media.AddOption(":avcodec-hw=d3d11va");
            videoStatus.Text = "Ühendan videovooga …";
            // Never print stream URL: it may contain login credentials.
            WriteLog("Videovoo avamine: " + uri.Scheme + ", " + decoder.SelectedItem + ", " + transport.SelectedItem);
            if (!player.Play(media)) videoStatus.Text = "Videovoogu ei saanud alustada.";
        }
        catch (Exception ex) { videoStatus.Text = "Video viga: " + ex.GetType().Name;
            WriteLog("Video viga: " + ex.GetType().Name); }
    }
    private static string Safe(string value) => value.Replace("\r", " ").Replace("\n", " ")[..Math.Min(180, value.Length)];
    private void WriteLog(string message)
    {
        if (!IsDisposed) log.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + message + Environment.NewLine);
    }
    private void SetVideoStatus(string value)
    {
        if (IsDisposed || !IsHandleCreated) return;
        try { BeginInvoke(new Action(() => { if (!IsDisposed) videoStatus.Text = value; })); }
        catch (InvalidOperationException) { }
    }
    private void SetLog(string value)
    {
        if (IsDisposed || !IsHandleCreated) return;
        try { BeginInvoke(new Action(() => WriteLog(value))); }
        catch (InvalidOperationException) { }
    }
    private void Shutdown()
    {
        player?.Stop(); view?.Dispose(); player?.Dispose(); lib?.Dispose();
        cloud.Dispose(); closing.Dispose();
    }
}