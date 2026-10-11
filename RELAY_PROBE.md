# Joolink2 Relay Test (experimental, 1.6.0)

This is a **separate installable Android app** (applicationId
`ee.joolinkviewer.relaytest`) built from Joolink Viewer 1.5.1. It does
not replace the existing Joolink Viewer and stores its own cloud session
encrypted using Android Keystore.

## Scope

The working Joolink HTTPS account login at
`https://qacloudapi.jooancloud.com`, cached camera list, local
RTSP/ONVIF player and decoder options remain available.

Each camera card and the main menu now contain a
**"Katseta serveri kaudu videot (KP2P/TURN)"** option.

On explicit user action, the diagnostic:
1. Extracts possible device UID from the saved Joolink camera record,
   or permits manual correction;
2. Queries the Jooan/Juanvision KP2P research route
   `http://ngw.dvr163.com/address/client`;
3. Attempts the gateway WebSocket/ARQ handshake;
4. Requests a TURN relay endpoint and reports whether one is returned.

**It does NOT stream, decode or display camera video yet.** The Joolink
cameras may use a distinct TUTK protocol rather than this KP2P route.
A failed test is not proof cloud viewing is impossible.

IMPORTANT: the third-party KP2P discovery route above is not the
already-verified Joolink account HTTPS endpoint. This route uses plain
HTTP and transmits only the supplied camera UID; it never receives
Joolink login credentials or cloud tokens. Only run this test if
comfortable with that exposure.

## Phone-only test

1. Open GitHub Actions:
   https://github.com/orogdor/node-corse-2-web-server/actions/workflows/cloud-relay-probe.yml
2. Select the newest successful run for branch
   `joolink-cloud-relay-probe` and its
   `Joolink-Cloud-Relay-Probe-APK` artifact.
3. Extract the downloaded ZIP and install `app-debug.apk`.
4. Sign in to Joolink in **Joolink2 Relay Test** and refresh cameras.
5. Turn off Wi-Fi and use mobile data. Open JA-Q7G or JA-Q7T.
6. Tap **Katseta serveri kaudu videot (KP2P/TURN)**.
7. Send back the stage/result/error **without UID, account password,
   session token or personal identifiers**.

The test is deliberately isolated in a separate app and branch. Do not
merge into the stable branch until compatibility and security review.

## Sources

- Jooan/Juanvision interoperability research:
  https://github.com/TomasReiff/jooan-kp2p-rtsp-bridge
- ThroughTek relay background:
  https://github.com/TutkKalay/Kalay_Kit_Sample_App
