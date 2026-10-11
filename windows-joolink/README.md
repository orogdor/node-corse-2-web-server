# HomeJoolink Windows v1.0.0 TEST

Windows 10/11 x64 .NET 8 WinForms client for Joolink/Jooan cameras. Not affiliated with the manufacturer.

Functions: experimental direct HTTPS cloud account login, cloud camera list, LibVLC RTSP/RTSPS/HTTP video, decoder choice (automatic / CPU / D3D11VA), diagnostics with no credentials or tokens logged.

**Important:** Vendor remote cloud live-video/P2P/LinkVisual decoding is NOT implemented. A cloud camera list does not imply video will play. To display video, supply a working RTSP/HTTP camera stream URL, optionally via your own VPN. Server login may reject an unofficial client.

No passwords or tokens are saved to disk. Windows test app is unsigned. Extract the WHOLE ZIP, then run HomeJoolink.exe next to its VLC/NET dependencies. Do not move the EXE alone.

The code ports the existing experimental Android Joolink Viewer v1.5.1 cloud REST implementation. VideoLAN packages have separate LGPL and applicable native component licenses.

Build: dotnet publish windows-joolink/HomeJoolink.csproj -c Release -r win-x64 --self-contained true -o publish


## Windows single-file .exe (v1.0.1)

GitHub Actions publishes `HomeJoolink-Windows-v1.0.1.exe` as one self-contained Windows x64 application without ZIP or separate .NET installation. To accommodate LibVLC, the runtime extracts native components under the user's private %TEMP%/.net folder on launch; this is an expected implementation detail. It does not need an installer. The EXE is unsigned, so Windows SmartScreen may display an unknown publisher warning.

There is a --self-test <outputfile> mode used during the CI Windows build to verify VLC library initialization without requiring a camera connection. This does not verify remote cloud video.

## Windows v1.0.2
- Window fits Windows' usable work area instead of using a fixed 1250 x 790 size. Left panel controls scale with the tab width and Windows DPI.
- Experimental Joolink device-list request variants: original force_query_bind=Y and without the flag. Select manually; no undocumented endpoints or repeated background probing.
- E_000_002 appears as a server rejection with only response field *names*, error code and mode in copyable diagnostic log. Its exact meaning is unknown; a successful login does not prove that the camera-list API has accepted the session.
- Cloud video / P2P is still not supported. No credentials or session tokens are written to log or disk.


## Windows v1.0.3 startup fixes
- Primary-display-centered launch, more tolerant minimum pane dimensions.
- EXE has an additional --ui-self-test mode that constructs and displays the real WinForms UI on Windows.
- Start errors show a message and write %LOCALAPPDATA%\\HomeJoolink\\last-startup-error.txt.
- GitHub Actions verifies actual main-window construction in addition to VLC initialization. Real camera-cloud live video is still not supported.

## Windows v1.0.4 - quick-start via one EXE installer

Download `HomeJoolink-Windows-v1.0.4-Setup.exe`, run setup once. Then use the Start menu HomeJoolink shortcut to start the installed app. Installer includes all .NET and VLC files, installed under the user's LocalAppData/Programs/HomeJoolink. Uninstall is available in Windows Settings -> Apps.

The installer is a **single downloadable EXE**, but unlike v1.0.3's self-extracting runtime bundle, the installed app consists of normal files. The initial installer may take longer, but subsequent starts need not decompress the entire embedded runtime. GitHub CI tests launching the real GUI and LibVLC after installation. Windows Defender/SmartScreen can still delay first launches. This is an unsigned test installer, not a vendor application.

As before, the experimental Joolink cloud device list might return E_000_002 and live video through the manufacturer's P2P/cloud network is **not implemented**.


## Windows v1.0.5 — corrected vendor cloud list payload (unverified live)
Inspected the account owner's `Joolink-original-base.apk` offline. 
`NewMainPagePresenter.getDeviceList` calls `HeaderHelper.getV3Header2()` and sends `open_id_app_key` from `AMManager.sAliAppKey`, `force_query_bind` as "Y" or "N", as well as version/device/package fields. 
`AMManager.initQiaoanzhilianGp()` contains three public GP app-key choices. 
The previous Windows client omitted `open_id_app_key` and omitted the bind flag in the alternative mode.
The fix adds all three GP key variants to the UI, always supplies Y/N, uses en_US fallback language and vendor-like numeric timestamp sequence identifiers. 
A CI request-schema test validates six combinations using dummy credentials only.

This improves payload parity but the result is **not yet tested against the user's account**. Never copy a real session token or password into any diagnostic logs. Remote cloud video/ThroughTek P2P integration is still incomplete.

## Windows v1.0.6 — closer to original APK request version and error semantics

Offline inspection of the user's own original Android APK:
- The AndroidManifest.xml declares versionName **5.3.8.2** (not 1.0). The original GetDeviceList header uses AppUtil.getAppVersionName for client_version and app_version in the request body. Both are now 5.3.8.2, including the login request's app_version.
- HeaderHelper.getV3Header2 uses Build.MODEL. The Windows camera request can be tested with the existing "Windows PC" identifier or the Samsung Galaxy S24 model identifier "SM-S921B"; default remains Windows PC. Login's device model remains Windows PC in this experimental client.
- The APK's resources.arsc translates **E_000_002** to "Connection failed, server error, please try again later". The different code E_000_003 is for expired user information. The server response still does not reveal which underlying condition triggered E_000_002.
- The app remains experimental. Do not submit account credentials or private tokens in diagnostic reports. No confirmed working manufacturer cloud live-video implementation yet.
