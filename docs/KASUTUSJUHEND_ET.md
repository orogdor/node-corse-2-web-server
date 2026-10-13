# HomeJoolink 2.2 – TestLab, Elisa Android TV ja kaugvaatamine

## Valmis arendused

- Android APK Lite (Media3) ja VLC-ühilduvusvariant, lähtudes 1.1.0 tööle saadud videomängijast.
- Olemasolev kohalik RTSP kaamerapilt: näide rtsp://192.168.8.108:5540/live/ch0.
- ONVIF PTZ juhtimise jaoks eraldi IP-aadress (192.168.8.109:80 ei pruugi olla sama kaamera).
- RTSP/TCP video edasi saatmine pordil 8554, iga vaataja eraldi ülesvoolu ühendusega, limiit 8 klienti.
- Android foreground service, Android TV launcher, seadistatav pärast boot'i käivitamine.
- Ainult eraldi lubades Tailscale VPN kaudu 100.64/10 võrgust vaatajad. Avalikku internetti otse ei avata.
- Pythonis virtuaalne RTSP + ONVIF testkaamera ja Java relay automaattestid.

## Seadistamine koduses võrgus

1. Ühenda kaamera ja Android TV boks samasse usaldatud koduvõrku.
2. Installi Lite APK Android TV boksi; VLC versioon sobib vaid ARM64 seadmega.
3. Ava äpis Video edasi saatmine / Kaamerasaatja.
4. Sisesta kaamera RTSP URL, näiteks rtsp://192.168.8.108:5540/live/ch0, kui sinu kaamera seda kasutab.
5. Vajuta Käivita saatja. Kasuta nuppu Kontrolli, kas kohalik port vastab.
6. Ava teisest samas võrgus olevast seadmest ekraanil toodud rtsp://BOKSI_IP:8554/live/ch0 (RTSP TCP).
7. Vajadusel märgi pärast edukat testi automaatkäivituse lüliti ja taaskäivita teenus.
8. Kontrolli päris boksi aku-/taustatöö- ja autostardi-piiranguid. Operaatori tarkvara võib taustateenused lõpetada.

## Tailscale VPN kaugvaatamine

1. Installi Tailscale Android TV boksi ja telefoni või arvutisse. Ühenda mõlemad oma kontrollitud tailnetti.
2. Kontrolli, kas boksil on 100.64.0.0/10 IP (näiteks 100.101.102.103).
3. Märgi HomeJoolinkis Luba Tailscale VPN-vaatajad ja vajuta uuesti Käivita saatja.
4. Vali boksis näidatud rtsp://100.101.102.103:8554/live/ch0. Ühenda teisest seadmest, kui Tailscale on aktiivne.
5. Kui Elisa boksis Tailscale ei tööta, on vaja eraldi VPN-lüüsi. Kui mobiilseadmel on Tailscale, ei tähenda see automaatselt, et boks on VPN-iga ühendatud.

**Turvalisus:** Ära suuna porti 8554 ruuteris avalikku internetti. Edastajal puudub oma kasutaja autentimine; eelda usaldatud lokaalvõrku või piiratud Tailscale võrku. Videopakkide krüpteerimine üle interneti toimub Tailscale VPN-i sees. Kaamera RTSP salasõnu ei salvestata. ONVIF liikumiskäske ei proksita serveri kaudu, kui ONVIF ei ole otse kättesaadav.

## Virtuaalne kaamera (ilma riistvarata)

Nõuab Python 3 ja JDK 17. Valikuliselt ffmpeg H.264 videopildi genereerimiseks.

    python3 -m testlab.mock_camera --host 127.0.0.1 --rtsp-port 9554 --onvif-port 8800

Testkaamera RTSP: rtsp://127.0.0.1:9554/live/ch0

Testkaamera ONVIF: http://127.0.0.1:8800/onvif/device_service

Kui ffmpeg on saadaval ja sisaldab libx264 kodeerijat, genereeritakse videopilt; muidu saadetakse test-RTP, mis ei ole dekodeeritav.

Täistest (käivitatakse ka GitHub Actionsis):

    python3 -m unittest discover -s testlab -p 'test_*.py' -v

Automaatne kontroll katab RTSP DESCRIBE / SETUP / PLAY, RTP interleaving, ONVIF capabilities ja PTZ käsud, mitu vaatajat, uuesti ühendamise ning Java relay ACL reeglid.

## Teadaolevad piirangud

- TestLab ei simuleeri tootja Joolinki pilve-P2P / Kalay / LinkVisual protokolli.
- Android TV seadmel tuleks testida 24h stabiilsust, automaatkäivitust pärast taaskäivitust ja Tailscale kliendi käivitumist.
- Päris kaamerad võivad kasutada RTSP UDP-d, RTSP Digest autentimist või tootjapõhist RTSP seadistust, mida testlabor ei kata.
- Piirang 8 samaaegset vaatajat, kuid iga vaataja loob oma RTSP ühenduse kaamerasse (võib suurendada kaamerakoormust).
- PTZ juhtimise IP ja video IP võivad tähistada eri füüsilisi kaameraid; kontrolli enne suunanoolte kasutamist.
