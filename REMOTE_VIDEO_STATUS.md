# HomeJoolink 1.7.0 — remote video handoff (NOT embedded cloud playback)

## What genuinely works
- Sign in with your own account directly to the existing Joolink HTTPS cloud API.
- Load the camera list, online status, and models.
- Each cloud camera card has **Ava kaugvideo Joolinkis**: launches the official installed Joolink app, which has the proprietary SDK license and can display video over the Internet. **User must select the camera there**.
- For a separately available, valid RTSP/HTTP/HLS camera stream, select **Seadista eraldi RTSP/HLS videovoog**; HomeJoolink's existing LibVLC software/hardware decoder displays it in-app.
- Original app is not rebranded/repackaged, SDK libraries and license keys are NOT copied.
- Installs separately with package name `ee.homejoolink.app.remote17`.

## What does NOT work yet
- HomeJoolink cannot independently render the licensed vendor Kalay/LinkVisual cloud video.
- No secure deep-link into the camera: the original APK manifest has private
  `CameraPlayerActivity` and `AliCloudVideoPLayerActivity`; neither is exported.
  Only its public launcher may be opened from another Android app.
- The older UID test to `ngw.dvr163.com` is not the vendor's authenticated Joolink
  stream and can return HTTP 500. This failure does not characterize Kalay status.

## Why
ThroughTek Kalay SDK requires a valid SDK license and application package-bound
authorization. An APK from the original Joolink vendor is not a license to redistribute
their SDK inside a different public app. Device AV credentials are also not identical
to the Joolink cloud session token.

## To enable native vendor cloud video in HomeJoolink
Obtain a properly licensed Android Kalay SDK for package
`ee.homejoolink.app.remote17`, an authorized vendor-issued SDK license/private key,
and documentation or credentials for Joolink camera AV authentication. Then implement
transport initialization, `IOTC_TCPRelayOnly_TurnOn`, verified TCP relay session,
and H264/H265 decoding inside the app. The original APK strongly indicates that this
is technically possible; it is not proof the custom app currently implements it.

## Testing
1. Install HomeJoolink 1.7.0 **and** the official Joolink app.
2. Turn OFF Wi-Fi, use mobile data, and log in to the official Joolink app.
3. In HomeJoolink sign in to your account and choose an online camera.
4. Tap **Ava kaugvideo Joolinkis**. You are taken to the official app.
5. Select the camera inside the official app; it should use the vendor's own Internet path.
6. If you already have an RTSP/HLS URL, bind it using the separate button to view within HomeJoolink.

Source for licensing: https://www.throughtek.cn/help-appDevelop/
