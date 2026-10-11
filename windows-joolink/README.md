# HomeJoolink Windows v1.0.0 TEST

Windows 10/11 x64 .NET 8 WinForms client for Joolink/Jooan cameras. Not affiliated with the manufacturer.

Functions: experimental direct HTTPS cloud account login, cloud camera list, LibVLC RTSP/RTSPS/HTTP video, decoder choice (automatic / CPU / D3D11VA), diagnostics with no credentials or tokens logged.

**Important:** Vendor remote cloud live-video/P2P/LinkVisual decoding is NOT implemented. A cloud camera list does not imply video will play. To display video, supply a working RTSP/HTTP camera stream URL, optionally via your own VPN. Server login may reject an unofficial client.

No passwords or tokens are saved to disk. Windows test app is unsigned. Extract the WHOLE ZIP, then run HomeJoolink.exe next to its VLC/NET dependencies. Do not move the EXE alone.

The code ports the existing experimental Android Joolink Viewer v1.5.1 cloud REST implementation. VideoLAN packages have separate LGPL and applicable native component licenses.

Build: dotnet publish windows-joolink/HomeJoolink.csproj -c Release -r win-x64 --self-contained true -o publish
