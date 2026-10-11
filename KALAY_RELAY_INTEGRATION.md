# HomeJoolink — Kalay TCP relay only integration

## Evidence inspected (2026-10-11)
Private user-supplied 101 MB original Joolink Android base APK; no application passwords,
device IDs, tokens, or binary dependencies have been copied to this repository.

- Original native arm64-v8a **libIOTCAPIs.so** exports `IOTC_TCPRelayOnly_TurnOn`,
  `IOTC_Connect_ByUIDEx`, `IOTC_Session_Check_Ex`, and `IOTC_Set_Connection_Option`.
- Original Java `com.tutk.IOTC.IOTCAPIs` declares `IOTC_TCPRelayOnly_TurnOn()`
  and the session-check methods.
- Original `com.jooan.p2p.P2PManager` calls
  `com.jooan.p2p.camera.P2PCamera.TK_initIOTCWithLicenseKey(...)`.
- `com.jooan.p2p.P2PManager` constructs a `P2PCamera`, connects it to
  a UID, starts AV, and attaches a software/hardware video monitor.
- A separate `com.aliyun.iotx.linkvisual.media.video.player.LivePlayer`
  and native `liblinkvisual.so` also exist. Camera compatibility with
  either SDK must be determined from the account's camera family metadata.
- `ngw.dvr163.com` was only an unrelated KP2P interoperability experiment;
  HTTP 500 does **not** indicate the Joolink account's Kalay route cannot work.

## Goal
**Joolink account + online device UID -> Kalay TCP relay only -> authenticated
AV media -> Android decoder**, without LAN RTSP, VPN or direct P2P media transport.

Per ThroughTek's published Kalay release notes,
`IOTC_TCPRelayOnly_TurnOn` requests *TCP relay only*.
Verify actual established session mode after connect using
`IOTC_Session_Check_Ex`. Historical Kalay `st_SInfoEx` values:
`Mode=1` is relay, `RelayType=2` is TCP relay. If a non-relay mode
is detected, stop playback and disconnect.

## Integration implementation plan
1. Import a **properly licensed** modern ThroughTek Kalay Android
   SDK into the Android project, including compatible ABI libraries.
   Do NOT copy/re-distribute proprietary Joolink APK libraries or keys
   into this public repo.
2. Obtain ThroughTek `sdk licenseKey` and app-package-bound
   `privateKey` from the vendor/authorized SDK provider; do not hardcode
   them in this public repository.
3. Initialize the licensed Kalay runtime per its own version's
   documented initialization sequence. Enable `IOTC_TCPRelayOnly_TurnOn`
   **before** initiating a device session when the SDK supports it.
   Newer SDKs may use a different equivalent transport option.
4. Get the *device* UID from `jooan_info` for a selected camera that
   currently reports online. Device UID is not the cloud-account session token.
   Do not send identifiers to third-party KP2P discovery servers.
5. Connect through the SDK's supported UID method; establish authenticated
   AV channel using valid camera credentials. Joolink cloud-account login
   alone is not a substitute for the device's AV authentication.
6. Require an actual *relay-only session*, verify check result and
   reject direct P2P or LAN transport (no silent fallback).
7. Bind the SDK's video frame callbacks to MediaCodec/software decoder
   and a SurfaceView/TextureView. Implement timeouts, lifecycle cleanup,
   relay reconnect and safe errors. Keep existing decoder selector.
8. Test on JA-Q7G and JA-Q7T **over mobile data with WiFi off**:
   login -> online camera -> SDK initialized -> UID connected ->
   session Mode=1 and RelayType=2 -> first H264/H265 frame -> video view.
   Confirm stream stops cleanly and never downgrades to direct P2P.

## Blocker verified in vendor documentation
ThroughTek requires an SDK licenseKey and separately an app package-name-
bound privateKey for Kalay SDK application development. A key linked to
the original Joolink app package is not a portable license for HomeJoolink.
Without an authorized license and camera AV authentication we cannot
truthfully claim that the custom app will be able to play Joolink cloud video.

Public references:
- https://www.throughtek.cn/help-appDevelop/
- https://www.throughtek.cn/help-application-android/
- https://github.com/TutkKalay/Kalay_Kit_Sample_App
- https://www.throughtek.cn/documentcenter/578.html

This document records observations, not a working streaming integration.
