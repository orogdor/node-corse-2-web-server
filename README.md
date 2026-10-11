# HomeJoolink — Android test app (1.6.2)

This is the experimental cloud relay diagnostic application for Joolink/Jooan cameras. Android app name: **HomeJoolink**, package: `ee.homejoolink.app.diag162`, APK file: **HomeJoolink-1.6.2.apk**. It installs alongside the previous Joolink Viewer. It can sign in to Joolink, list cameras and test a vendor UID/TURN server-relay handshake, but **does not yet play vendor cloud video**. The KP2P test can fail if a camera uses TUTK instead.

**Build:** [GitHub Actions](https://github.com/orogdor/node-corse-2-web-server/actions) → **Build HomeJoolink Android APK** → successful run → artifact **HomeJoolink-1.6.2-APK**. Extract the artifact ZIP and install `HomeJoolink-1.6.2.apk` on an arm64 Android phone. For details read [RELAY_PROBE.md](RELAY_PROBE.md).

---

# Joolink Viewer — Android 1.0

Androidi testirakendus (eestikeelne) Joolinki/Jooani või teiste IP-kaamerate RTSP-video kuvamiseks **kohalikus WiFi-võrgus**. Pole seotud Joolinki tootjaga.

## Funktsioonid

- Koduse WiFi ühe `/24` alavõrgu otsing pordil 554, 5540, 8554 ja 10554.
- RTSP DESCRIBE kontroll seitsmele levinud aadressirajale; lubab aadressi käsitsi parandada.
- Mitme kaamera aadressi ja nime salvestamine; paroole ei salvestata.
- LibVLC 3.7.0; nelja režiimi valik: tarkvaraline/riistvaraline dekodeerimine ja TextureView/SurfaceView.
- TCP RTSP-video; täisekraani / maastikuvaate lüliti.

## Paigaldamine APK-na

GitHub Actions ehitab `app-debug.apk`. Ava **Actions → Build Joolink Viewer APK → viimane roheline jooks → Artifacts → Joolink-Viewer-APK**. Laadi ZIP alla, paki lahti ja ava APK Androidis. Android võib nõuda teadmata allikast installimise luba.

Android Studio abil saab koostada `Build → Build APK(s)`.

## Kasutamine

1. Ühenda telefon samasse WiFi-sse kui kaamera; mobiilside/võõras VPN ei sobi.
2. Puuduta **Otsi WiFi-võrgust kaameraid**.
3. Puuduta leitud aadressil **Leia videovoog ja kasuta**. Kui automaatne raja kontroll ebaõnnestub, proovi käsitsi näiteks `rtsp://192.168.1.123:5540/live/ch0` või `rtsp://192.168.1.123:554/live/ch00_0`.
4. Vajadusel lisa kaamera kasutajanimi ja parool. Need võivad erineda Joolinki pilvekonto omadest.
5. Puuduta **Ava kaamerapilt**, vaikimisi kasutatakse tarkvaralist dekoodrit + TextureView'd. Kui videopilt on värvidega vigane, võrdle kõiki nelja režiimi.
6. Salvesta kaamera nimi ja RTSP-aadress, et järgmine kord kiiresti valida.

## Piirangud ja turvalisus

- Mõned Joolinki mudelid **ei võimalda RTSP-d**. Sellise kaamera pilti see versioon kuvada ei saa. Joolinki pilveserverit ega P2P-protokolli see projekt ei rakenda.
- ONVIF/RTSP-ga ühilduvus tuleb konkreetse kaameraga üle testida; ükski avatud RTSP-port ei garanteeri mängitavat videovoogu.
- Kaamera kasutajanime võib salvestada, salasõna mitte. Kui lisad parooli käsitsi RTSP-aadressi sisse, eemaldatakse see salvestamisel automaatselt.
- Otsing on piiratud telefoni aktiivse koduse WiFi-aadressivahemikuga ja kontrollib ainult nelja TCP-porti; ei skaneeri internetti.
- Sama võrgu külge lülitatud kaamera võib olla isoleeritud külalisvõrku; sel juhul otsing ei pruugi seda leida.
- Pilvekaameratele kaugelt ligipääs, PTZ, video salvestamine ja teavitused ei kuulu esimesse versiooni.
- See projekt ei võta ühendust välise serveriga, v.a kaameratele suunatud RTSP päringud.

## Ehitamine

Nõuab JDK 17, Android SDK platform 35, Gradle 8.9 ja võrguühendust sõltuvuste allalaadimiseks.

```sh
gradle assembleDebug
```

APK tee: `app/build/outputs/apk/debug/app-debug.apk`.

## Õiguslik

Avatud lähtekood (MIT). LibVLC on VideoLANi projekt oma LGPL litsentsitingimustega. Äpp on tootjast sõltumatu.

## Android arm64 testversioon 1.0.2
See avalik haru ehitab ainult arm64-v8a APK (nt Samsung Galaxy S24). 32-bitised Android-telefonid pole toetatud. Videokaamera RTSP toe olemasolu tuleb eraldi testida.
