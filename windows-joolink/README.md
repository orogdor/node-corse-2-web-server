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
