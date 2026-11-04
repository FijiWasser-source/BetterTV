# Klar – werbefreie Startseite für Android TV / Google TV

Ein ruhiger, schlichter Startbildschirm statt der Android-TV-Oberfläche mit
Empfehlungs- und Werbezeilen – gestaltet nach Apples Designsprache (tvOS):
dunkle Flächen, abgerundete Kacheln ohne Rahmen, Fokus durch Anheben mit Lichtreflex,
App-Name nur unter der gewählten Kachel, Hintergrund färbt sich sanft passend zur App,
Menüs als schwebende Tafeln mit weißer Fokus-Schaltfläche.

![Vorschau](app/src/test/snapshots/images/de.klar.tvlauncher_PreviewTest_home_home.png)

*Vorschau mit Platzhalter-Bannern; auf dem TV erscheinen die echten Banner der Apps.*

Läuft ab Android 5, also auch auf dem **Sony BRAVIA KD-65XD7505** (Android TV 6/7)
und auf neueren Google-TV-Geräten.

- **Große App-Kacheln**, mit der Fernbedienung bedienbar (nutzt die 16:9-Banner der TV-Apps)
- **Anheften & Ausblenden**: OK lange drücken oder Menü-Taste → *An den Anfang heften*,
  *Nach vorne/hinten*, *Ausblenden*, *App-Info*
- **Uhr, Datum & Wetter** (Open-Meteo: ohne Konto, ohne API-Schlüssel)
- **Keine Werbung, keine Empfehlungen, kein Tracking.** Einzige Internetverbindung: das Wetter
  (es wird nur der Ort übertragen). Einstellungen bleiben auf dem Gerät.

## 1. APK bekommen

- Fertig gebaut: Im GitHub-Tab **Actions → „TV-Launcher bauen“** das Artefakt
  `klar-launcher-apk` herunterladen, **oder**
- selbst bauen (Android SDK nötig): `cd tv-launcher && ./gradlew assembleDebug`
  → `app/build/outputs/apk/debug/app-debug.apk`

## 2. Am TV vorbereiten

1. Entwickleroptionen an: 7× auf **Build** klicken – Sony (Android 6/7): *Einstellungen → Info → Build*;
   Google TV: *Einstellungen → System → Über → Android TV OS-Build*.
2. In den *Entwickleroptionen* **USB-Debugging** einschalten (bei Sony ist ADB damit in der Regel auch übers Netzwerk erreichbar; `find-tv.py` zeigt es an).
3. Die IP-Adresse findet `tools/find-tv.py` (siehe 3.) – oder am TV unter *Einstellungen → Netzwerk*.
4. Am Computer [adb (Platform Tools)](https://developer.android.com/tools/releases/platform-tools) installieren.
   Beim ersten Verbinden auf dem TV „Zulassen“ bestätigen.

## 3. Fernseher im Netzwerk finden

Auf einem Computer im **selben Netz** wie der TV (TV eingeschaltet):

```bash
python3 tools/find-tv.py
```

Das Skript fragt per UPnP/SSDP nach Geräten und erkennt Sony-TVs über deren
eingebaute Schnittstelle; notfalls durchsucht es das Heimnetz (/24). Es zeigt IP,
Modell und ob ADB erreichbar ist. In allen Befehlen unten kann statt der IP auch `auto` stehen.

## 4. Einrichten (mit dem Skript)

```bash
./tools/tv-setup.sh auto install           # Klar installieren (TV wird automatisch gesucht)
./tools/tv-setup.sh auto dns               # Werbeblocker-DNS (bei Android < 9: Anleitung)
./tools/tv-setup.sh auto home              # Home-Taste -> Klar (Original-Startseite aus)
./tools/tv-setup.sh auto status            # Kontrolle
```

Danach in Klar die Kachel **Einstellungen → Wetter-Ort** setzen.

**Alles rückgängig machen:**

```bash
./tools/tv-setup.sh auto restore           # Google-Startseite wieder an
./tools/tv-setup.sh auto dns-off           # DNS wieder automatisch
```

### Warum „home“ nötig ist

Google TV lässt die Home-Taste fest auf die eigene Startseite zeigen, auch wenn ein anderer
Launcher installiert ist. Der Befehl deaktiviert (nicht löscht!) die Google-Startseite für den
Nutzer – dann landet die Home-Taste bei Klar. Hinweise:

- Systemupdates können die Google-Startseite wieder aktivieren → einfach `home` erneut ausführen.
- Einige Google-TV-Funktionen hängen an der Original-Startseite (z. B. Profilwechsel,
  Google-Assistant-Ergebnisseiten, Live-TV-Leiste mancher Hersteller). Sprachsuche in Apps
  und alle Apps selbst funktionieren weiter.
- Wenn etwas hakt: `restore` – danach ist alles wie vorher.

**Ohne ADB / ohne Risiko:** Google TV hat den **„Nur-Apps-Modus“**
(*Einstellungen → Konten & Anmeldung → dein Konto → Nur-Apps-Modus*). Er entfernt alle
Empfehlungen und Werbeflächen aus der Original-Startseite – deutlich ruhiger, aber nicht so
schlicht wie Klar.

### Sony BRAVIA (Android 6/7, z. B. KD-65XD7505)

- „Privates DNS“ gibt es erst ab Android 9. `dns` erkennt das und zeigt stattdessen, wie
  du AdGuard-DNS (`94.140.14.14` / `94.140.15.15`) am TV (statische IP-Einstellungen) oder –
  besser für alle Geräte – im Router einträgst.
- Die Original-Startseite heißt hier `com.google.android.leanbacklauncher`; `home` deaktiviert
  sie, die Home-Taste führt dann zu Klar. Sony-Menüs (Action-Menü, Eingänge, Bild) bleiben.
- Am TV zusätzlich abschalten, falls vorhanden: *Samba Interactive TV* (Inhaltserkennung)
  und Sony-Empfehlungen/Datenschutz-Optionen in den Einstellungen.
- Der TV ist von 2016 und eher langsam: Klar verzichtet deshalb auf Unschärfe-Effekte und
  nutzt nur kurze, leichte Animationen.

## 5. Werbung & Tracking im Netz

| Maßnahme | Wo | Effekt |
|---|---|---|
| Privates DNS `dns.adguard-dns.com` | Skript `dns` | Blockt Werbe- und Tracking-Server systemweit (Banner in Apps, Telemetrie). |
| Werbe-ID löschen | *Einstellungen → Datenschutz → Werbung → Werbe-ID löschen* | Keine personalisierte Werbung über die ID. |
| Nutzungs-/Diagnosedaten aus | *Einstellungen → Datenschutz → Nutzung & Diagnose* | Weniger Daten an Google. |
| Hersteller-Tracking aus | je nach Marke: „Viewing Information“, „Smart-TV-Erfahrung“, „ACR“, „Inhaltserkennung“ (Philips, TCL, Hisense, Sony) | Stoppt die Auswertung, was du schaust. |

Statt AdGuard geht jeder DNS-over-TLS-Anbieter, z. B. `dns.mullvad.net` (Mullvad mit Werbeblocker:
`adblock.dns.mullvad.net`) oder ein eigener Pi-hole/AdGuard Home mit DoT:
`./tools/tv-setup.sh <IP> dns adblock.dns.mullvad.net`.

**Ehrlich gesagt:** DNS-Blocker entfernen **keine YouTube-Werbung** (Werbung und Videos kommen
von denselben Servern) und keine Werbung, die Streamingdienste in ihre Abos einbauen.
Dafür braucht es YouTube Premium bzw. ein werbefreies Abo.

## Entwicklung

```bash
cd tv-launcher
./gradlew testDebugUnitTest   # Unit-Tests (Sortierung, Wetter-Texte)
./gradlew recordPaparazziDebug # Vorschaubild der Startseite neu rendern
./gradlew assembleDebug
```

Aufbau: `MainActivity` (Oberfläche & Dialoge), `AppLoader` (installierte Apps), `AppOrder`
(Sortierlogik, getestet), `Weather` (Open-Meteo), `Prefs` (lokale Einstellungen),
`Sheet` (tvOS-artige Menüs), `Tint` (App-Farbe für Kacheln/Hintergrund).
