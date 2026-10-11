using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace HomeJoolink;

internal sealed class JoolinkCloudClient : IDisposable
{
    private const string Origin = "https://qacloudapi.jooancloud.com";
    private const string Package = "com.jooan.qiaoanzhilian.gp";
    private readonly HttpClient http = new(new HttpClientHandler
    {
        AllowAutoRedirect = false, UseCookies = false,
        AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate
    }) { Timeout = TimeSpan.FromSeconds(18) };

    public sealed record Session(string UserId, string Token);
    public sealed record CloudCamera(string Name, string Model, string OnlineStatus);
    // No raw server body or token values are ever exposed through this exception.
    public sealed class DeviceListException : Exception
    {
        public string Code { get; }
        public string RequestMode { get; }
        public string ResponseFields { get; }
        public int HttpStatus { get; }
        public DeviceListException(string code, string requestMode, string responseFields, int httpStatus)
            : base("Joolinki kaameraloendi päring lükati tagasi (" + code + ").")
        {
            Code = code;
            RequestMode = requestMode;
            ResponseFields = responseFields;
            HttpStatus = httpStatus;
        }
    }

    private static string Id() => Convert.ToHexString(RandomNumberGenerator.GetBytes(12)).ToLowerInvariant();
    // The vendor's CommonUtil.getSeqno() uses a sortable local timestamp plus
    // a random numeric suffix; hexadecimal identifiers were used in v1.0.4.
    private static string SequenceNumber() =>
        DateTime.Now.ToString("yyyyMMddHHmmssfff", System.Globalization.CultureInfo.InvariantCulture)
        + RandomNumberGenerator.GetInt32(100, 1000).ToString(
            System.Globalization.CultureInfo.InvariantCulture);
    // Public identifiers extracted from the owner's original vendor APK:
    // AMManager.initQiaoanzhilianGp assigns sAliAppKey based on build variant.
    internal static readonly string[] PublicAppKeys =
        { "30837210", "30808266", "30816024" };
    // APK AndroidManifest.xml: versionName="5.3.8.2" (the original Google Play Joolink app).
    internal const string VendorAppVersion = "5.3.8.2";
    // The real Android app uses Build.MODEL; offer an explicit local test variant.
    // Different account/device models can use different model identifiers.
    internal static readonly string[] RequestPhoneModels = { "Windows PC", "SM-S921B" };
    internal const string CameraEndpoint = "/v3/am/query_device_list";
    internal static Dictionary<string, object> BuildCameraRequest(
        Session session, bool forceQueryBind, int keyVariant, int modelVariant = 0)
    {
        if (keyVariant < 0 || keyVariant >= PublicAppKeys.Length)
            throw new ArgumentOutOfRangeException(nameof(keyVariant));
        if (modelVariant < 0 || modelVariant >= RequestPhoneModels.Length)
            throw new ArgumentOutOfRangeException(nameof(modelVariant));
        string phoneModel = RequestPhoneModels[modelVariant];
        return new Dictionary<string, object>
        {
            ["header"] = Header(session.UserId, session.Token, phoneModel),
            ["open_id_app_key"] = PublicAppKeys[keyVariant],
            ["force_query_bind"] = forceQueryBind ? "Y" : "N",
            ["app_version"] = VendorAppVersion,
            ["phone_model"] = phoneModel,
            ["package_name"] = Package
        };
    }
    private static Dictionary<string, object> Header(string user = "", string token = "", string phoneModel = "Windows PC")
    {
        var d = new Dictionary<string, object>
        {
            ["seqno"] = SequenceNumber(), ["package_name"] = Package,
            ["client_version"] = VendorAppVersion, ["phone_model"] = phoneModel,
            // CommonUtil.getLanguge() maps unsupported locales to en_US.
            ["language"] = "en_US"
        };
        if (user.Length > 0) d["user_id"] = user;
        if (token.Length > 0) d["token"] = token;
        return d;
    }
    public async Task<Session> LoginAsync(string account, string password, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(account) || password.Length == 0)
            throw new InvalidOperationException("Sisesta konto ja parool.");
        account = account.Trim();
        bool email = account.Contains('@');
        var request = new Dictionary<string, object>
        {
            ["header"] = Header(), ["login_mode"] = email ? "1" : "0",
            ["email"] = email ? account : "", ["phone_no"] = email ? "" : account,
            ["password"] = password, ["app_version"] = VendorAppVersion,
            ["phone_model"] = "Windows PC", ["system_version"] = Environment.OSVersion.VersionString,
            ["client_id"] = "windows-" + Id(), ["package_name"] = Package
        };
        using var doc = await PostAsync("/v3/am/login", request, ct);
        var root = doc.RootElement;
        var payload = Child(root, "body") ?? Child(root, "data");
        if (!payload.HasValue)
            throw new InvalidOperationException("Server ei tagastanud seanssi. Kood: " + Clean(Get(root, "error_code", "code")));
        string user = Get(payload.Value, "user_id", "userId"), token = Get(payload.Value, "token", "user_token");
        if (user.Length == 0 || token.Length == 0)
            throw new InvalidOperationException("Sobiv seansitoken puudub. Kood: " + Clean(Get(root, "error_code", "code")));
        return new Session(user, token);
    }
    public async Task<IReadOnlyList<CloudCamera>> CamerasAsync(Session session, bool forceQueryBind,
        int keyVariant, int modelVariant, CancellationToken ct)
    {
        var request = BuildCameraRequest(session, forceQueryBind, keyVariant, modelVariant);
        using var doc = await PostAsync(CameraEndpoint, request, ct);
        var root = doc.RootElement;
        string code = Get(root, "error_code", "code");
        string mode = (forceQueryBind ? "force_query_bind=Y" : "force_query_bind=N")
            + ", GP-võti " + (keyVariant + 1) + ", telefon: " + RequestPhoneModels[modelVariant];
        if (code.Length > 0 && !new[] { "0", "200", "ok", "success" }.Contains(code, StringComparer.OrdinalIgnoreCase))
            throw new DeviceListException(Clean(code), mode, SafeFieldNames(root), 200);
        JsonElement? devices = FindDevices(root, 0);
        if (!devices.HasValue)
            throw new DeviceListException(code.Length > 0 ? Clean(code) : "NO_DEVICE_LIST", mode, SafeFieldNames(root), 200);
        var items = new List<CloudCamera>();
        foreach (var item in devices.Value.EnumerateArray())
        {
            if (item.ValueKind != JsonValueKind.Object) continue;
            var jo = Child(item, "jooan_info");
            var al = Child(item, "alilv_info");
            string name = First(Get(jo, "nick_name"), Get(al, "nickName"),
                Get(item, "device_name", "nickName"), "Kaamera " + (items.Count + 1));
            string model = First(Get(jo, "device_model"), Get(al, "productModel"), "Kaamera");
            string status = First(Get(al, "status"), Get(jo, "is_online"), "teadmata");
            items.Add(new CloudCamera(name, model, status));
            if (items.Count >= 100) break;
        }
        return items;
    }
    private async Task<JsonDocument> PostAsync(string path, object body, CancellationToken ct)
    {
        if (!path.StartsWith("/v3/am/", StringComparison.Ordinal))
            throw new InvalidOperationException("Keelatud API aadress.");
        using var req = new HttpRequestMessage(HttpMethod.Post, Origin + path)
        {
            Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json")
        };
        req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        req.Headers.CacheControl = new CacheControlHeaderValue { NoStore = true };
        using var reply = await http.SendAsync(req, HttpCompletionOption.ResponseHeadersRead, ct);
        if ((int)reply.StatusCode >= 300 && (int)reply.StatusCode < 400)
            throw new InvalidOperationException("Server suunas mujale, turvalisuse huvides peatatud.");
        if (!reply.IsSuccessStatusCode)
            throw new InvalidOperationException("Serveri HTTP viga " + (int)reply.StatusCode);
        if (reply.Content.Headers.ContentLength > 1_048_576)
            throw new InvalidOperationException("Liiga suur vastus.");
        await using var stream = await reply.Content.ReadAsStreamAsync(ct);
        using var output = new MemoryStream();
        var buffer = new byte[8192];
        int count;
        while ((count = await stream.ReadAsync(buffer, ct)) > 0)
        {
            if (output.Length + count > 1_048_576) throw new InvalidOperationException("Liiga suur vastus.");
            output.Write(buffer, 0, count);
        }
        output.Position = 0;
        try { return await JsonDocument.ParseAsync(output, cancellationToken: ct); }
        catch (JsonException) { throw new InvalidOperationException("Serveri vastus ei olnud korrektne JSON."); }
    }
    private static JsonElement? FindDevices(JsonElement root, int depth)
    {
        if (root.ValueKind != JsonValueKind.Object || depth > 3) return null;
        foreach (var key in new[] { "device_info_list", "device_list", "deviceList", "devices" })
            if (root.TryGetProperty(key, out var found) && found.ValueKind == JsonValueKind.Array)
                return found;
        foreach (var key in new[] { "body", "body_info", "data", "result", "payload" })
        {
            var sub = Child(root, key);
            if (sub.HasValue)
            {
                var devices = FindDevices(sub.Value, depth + 1);
                if (devices.HasValue) return devices;
            }
        }
        return null;
    }
    private static JsonElement? Child(JsonElement root, string key) =>
        root.ValueKind == JsonValueKind.Object && root.TryGetProperty(key, out var result)
            && result.ValueKind == JsonValueKind.Object ? result : null;
    private static string Get(JsonElement? root, params string[] keys) =>
        root.HasValue ? Get(root.Value, keys) : "";
    private static string Get(JsonElement root, params string[] keys)
    {
        if (root.ValueKind != JsonValueKind.Object) return "";
        foreach (string key in keys)
            if (root.TryGetProperty(key, out var value) &&
                value.ValueKind is JsonValueKind.String or JsonValueKind.Number or JsonValueKind.True or JsonValueKind.False)
                return value.ToString();
        return "";
    }
    internal static string SafeFieldNames(JsonElement root)
    {
        if (root.ValueKind != JsonValueKind.Object) return "JSON ei ole objekt";
        // Strict allowlist: dynamic JSON keys/values can include private data.
        string[] permitted = { "error_code", "error_msg", "message", "code", "body",
            "data", "result", "device_info_list", "device_list", "deviceList",
            "devices", "header", "seqno" };
        var seen = permitted.Where(k => root.TryGetProperty(k, out _)).ToArray();
        return seen.Length > 0 ? string.Join(", ", seen) : "teadmata vastuse struktuur";
    }
    private static string First(params string[] options) => options.First(x => !string.IsNullOrWhiteSpace(x));
    private static string Clean(string value) => value.Length == 0 ? "teadmata"
        : new string(value.Where(c => char.IsLetterOrDigit(c) || c == '_' || c == '-').Take(45).ToArray());
    public void Dispose() => http.Dispose();
}