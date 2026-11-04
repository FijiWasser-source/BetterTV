# Klar – werbefreie Startseite für Android TV / Google TV

Ein ruhiger, schlichter Startbildschirm statt der Google-TV-Oberfläche mit
Empfehlungs- und Werbezeilen.

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

1. *Einstellungen → System → Über* → 7× auf **Android TV OS-Build** klicken (Entwickleroptionen an).
2. *Einstellungen → System → Entwickleroptionen* → **USB-Debugging** (bzw. Netzwerk-/WLAN-Debugging) an.
3. IP-Adresse des TVs nachsehen: *Einstellungen → Netzwerk & Internet → dein WLAN*.
4. Am Computer [adb (Platform Tools)](https://developer.android.com/tools/releases/platform-tools) installieren.
   Beim ersten Verbinden auf dem TV „Zulassen“ bestätigen.

## 3. Einrichten (mit dem Skript)

```bash
./tools/tv-setup.sh 192.168.1.50 install   # Klar installieren
./tools/tv-setup.sh 192.168.1.50 dns       # Werbeblocker-DNS (AdGuard) setzen
./tools/tv-setup.sh 192.168.1.50 home      # Home-Taste -> Klar (Google-Startseite deaktivieren)
./tools/tv-setup.sh 192.168.1.50 status    # Kontrolle
```

Danach in Klar die Kachel **Einstellungen → Wetter-Ort** setzen.

**Alles rückgängig machen:**

```bash
./tools/tv-setup.sh 192.168.1.50 restore   # Google-Startseite wieder an
./tools/tv-setup.sh 192.168.1.50 dns-off   # DNS wieder automatisch
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

## 4. Werbung & Tracking im Netz

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
./gradlew assembleDebug
```

Aufbau: `MainActivity` (Oberfläche & Dialoge), `AppLoader` (installierte Apps), `AppOrder`
(Sortierlogik, getestet), `Weather` (Open-Meteo), `Prefs` (lokale Einstellungen).
