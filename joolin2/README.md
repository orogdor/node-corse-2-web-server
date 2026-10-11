# Joolin2 · mitteametlik Androidi videodekoodri test

Joolin2 on **katseline** Joolinki originaal-APK teisendus.
See proovib kasutada originaalse Joolinki videomootori
tarkvaralist või riistvaralist dekoodrit ning lisada kaamerapildi
vaatesse eraldi ⚙ Video menüü.

**Videodekooder:** automaatne / tarkvaraline / riistvaraline.
**Värviparandus:** originaal / U-V RGB järelfilter /
BT.601 → BT.709 / BT.709 → BT.601.

Erinev pakettnimi: `ee.joolin2.decoder`, et proovida originaaläpi kõrvale paigaldamist.

## Ehitus
GitHub Actions tõmbab Joolink 5.3.8.2 originaalfaili välisest
allikast ja *keeldub* jätkamast, kui selle SHA-256 ei võrdu
`87e89a2142249e020b2dcb9c5c964d4f06675c4de2370a990335b1bab962a395`.
Pärast Apktooliga muutmist lisatakse oma videoseadete kood, APK
allkirjastatakse testvõtmega ja avaldatakse GitHub Releasesis.

Lähtekood ei sisalda Joolinki tootja binaarfaile,
kaamera paroole ega kontotunnuseid.

## Piirangud
- Ainult APK ehituse edu **ei tõesta** videodekoodri korrektset tööd.
- Uus digitaalallkiri võib rikkuda Joolinki kontoga sisselogimise.
- Värvimuutused on katselised RGB järelfiltrid, mitte native NV12/NV21 teisendus.
- Tootja võib sisemisi klasse muuta; täisversiooni testimine toimub telefonis.
- Kui varasem Joolin2 oli paigaldatud teise allkirjaga, võib uuema
  paigaldamiseks olla vajalik eelmise Joolin2 eemaldamine.
- Joolinki kaubamärk ja tootja APK kuuluvad vastavatele õiguste omajatele;
  tegu ei ole ametliku Joolinki väljalaskega.

[Allalaadimised](https://github.com/orogdor/node-corse-2-web-server/releases/tag/joolin2-decoder-public-v1)
