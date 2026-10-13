# HomeJoolink 2.0 – local video / P2P relay groundwork

Based **directly** on the user's working Joolink Viewer 1.1.0 RTSP/LibVLC version.
Branch: homejoolink-lite-v2.

- **HomeJoolink-Lite**: Media3 RTSP over interleaved TCP using Android's video decoders. Much smaller download; experimental and **not yet tested on the real camera**. Can run on 32-bit Android TV, subject to device codec support.
- **HomeJoolink-VLC**: retains the exact earlier PlayerActivity video playback engine of v1.1.0, along with ONVIF and recordings. Bigger download; ARM64 only.
- Updated dark interface styling, maintained camera discovery and saved camera credentials behavior (password is not saved).
- **Relay Lab**: experimental local RTSP/TCP proxy on port 8554. Only for private LAN, RTSP/TCP sources and clients, without transcoding.
- Joolink proprietary cloud **P2P/TUTK/LinkVisual is not implemented**. The LAN relay cannot read a Joolink camera with no RTSP support.
- Never expose port 8554 to the internet. Use a secured VPN tunnel such as Tailscale for remote access. LAN RTSP relay has no independent authentication: camera's own RTSP authorization, if any, is passed through and client compatibility needs testing.
- Relay is an experimental initial version; the foreground Android service's behavior on a specific TV box must be tested. Video packet / RTSP protocol compatibility is not guaranteed.

APK builds: GitHub Actions in .github/workflows/android-apk.yml builds both flavors and publishes a release. Both have independent Android application IDs so the installed apps can be compared.

Source remains editable and the original 1.1.0 version remains available as a tag.
