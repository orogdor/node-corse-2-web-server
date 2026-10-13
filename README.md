# HomeJoolink 2.2 – TestLab / Android TV server / VPN relay

Uus eraldi arendusharu, mis jätab varasema töötava RTSP-vaaturi ja PTZ võimalused alles.

Kolm arendust:
1. TestLab: Python RTSP/ONVIF mock-camera ja integreeritud Java relay automaattestid.
2. Android TV: taustal töötav foreground RTSP teenus, opt-in boot autostart ja Android TV launcher.
3. Turvaline kaugvaatamine: ainult Tailscale VPN 100.64/10 kliendid, vaikimisi keelatud, ilma avaliku pordisuunamiseta.

RTSP relay on valmistatud eraldiseisva Java klassina, mida saab testida ilma Android seadmeta; toetab interleaved TCP, mitut klienti ja vastuste aadresside ümberkirjutamist. Ei kodeeri videot ümber.

Käivita virtuaalkaamera:
    python3 -m testlab.mock_camera --host 127.0.0.1 --rtsp-port 9554 --onvif-port 8800

Automaattestid:
    python3 -m unittest discover -s testlab -p 'test_*.py' -v

Kogu eestikeelne juhend: docs/KASUTUSJUHEND_ET.md.

**Mitte valmis:** Joolinki tootja P2P pilveprotokoll, täiesti automaatne NAT-läbimine, kaug-ONVIF juhtimise vahendamine, päris Elisa boksi ja kaamera pikad töökindlustestid. Kaugvaatamiseks peab Tailscale või muu sobiv VPN juba boksis ja telefonis olemas olema. RTSP proxy ise ei paku eraldi autentimist – ära ava porti avalikku internetti.
