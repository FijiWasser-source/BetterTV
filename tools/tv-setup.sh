#!/usr/bin/env bash
# Richtet den Klar-Launcher und einen werbeblockenden DNS auf einem Android-/Google-TV ein.
# Voraussetzung: "Entwickleroptionen" + "USB-/Netzwerk-Debugging" am TV aktiv, adb installiert.
#
# <TV-IP> darf "auto" sein: dann wird der TV per tools/find-tv.py im Netzwerk gesucht.
#
#   ./tools/tv-setup.sh <TV-IP> install            APK installieren
#   ./tools/tv-setup.sh <TV-IP> dns [hostname]     Privates DNS setzen (Standard: dns.adguard-dns.com)
#   ./tools/tv-setup.sh <TV-IP> dns-off            Privates DNS zurücksetzen
#   ./tools/tv-setup.sh <TV-IP> home               Original-Startseite deaktivieren -> Klar wird Home
#   ./tools/tv-setup.sh <TV-IP> restore            Original-Startseite wieder aktivieren
#   ./tools/tv-setup.sh <TV-IP> wallpaper <bild>   Hintergrundbild der Startansicht setzen
#   ./tools/tv-setup.sh <TV-IP> status             Aktuellen Zustand anzeigen
set -euo pipefail

IP="${1:-}"; CMD="${2:-}"
if [[ -z "$IP" || -z "$CMD" ]]; then sed -n '2,15p' "$0"; exit 1; fi

HERE="$(cd "$(dirname "$0")" && pwd)"
if [[ "$IP" == "auto" ]]; then
  echo "Suche den Fernseher im Netzwerk …"
  IP="$(python3 "$HERE/find-tv.py" --ip-only)" || { echo "Kein Sony-TV gefunden. Details: python3 $HERE/find-tv.py"; exit 1; }
  echo "Gefunden: $IP"
fi

APK="${APK:-$HERE/../tv-launcher/app/build/outputs/apk/debug/app-debug.apk}"  # oder: APK=~/Downloads/klar.apk
# Bekannte Original-Startseiten (Google TV, älteres Android TV) und deren Einrichtungs-Assistent.
STOCK_LAUNCHERS=(com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher)
STOCK_HELPERS=(com.google.android.tungsten.setupwraith)

if ! adb connect "$IP:5555" | grep -q "connected"; then
  echo "Keine ADB-Verbindung zu $IP. Am TV: Entwickleroptionen → USB-Debugging an, dann die Anfrage am TV erlauben."
  exit 1
fi
A=(adb -s "$IP:5555")
SDK="$("${A[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
installed() { "${A[@]}" shell pm list packages "$1" | grep -qx "package:$1"; }

case "$CMD" in
  install)
    [[ -f "$APK" ]] || { echo "APK fehlt: bauen (cd tv-launcher && ./gradlew assembleDebug) oder Pfad angeben: APK=/pfad/klar.apk $0 auto install"; exit 1; }
    "${A[@]}" install -r "$APK"
    ;;
  dns)
    if (( SDK < 28 )); then
      cat <<'TXT'
Dieser TV hat Android < 9 – "Privates DNS" gibt es dort nicht. Stattdessen:
  A) Am TV: Einstellungen → Netzwerk → (dein Netz) → IP-Einstellungen: Statisch
     IP/Gateway wie bisher lassen, DNS 1: 94.140.14.14  DNS 2: 94.140.15.15  (AdGuard)
  B) Besser für alle Geräte: dieselben DNS-Server im Router eintragen
     (FRITZ!Box: Internet → Zugangsdaten → DNS-Server) oder Pi-hole/AdGuard Home betreiben.
TXT
      exit 0
    fi
    HOST="${3:-dns.adguard-dns.com}"
    "${A[@]}" shell settings put global private_dns_mode hostname
    "${A[@]}" shell settings put global private_dns_specifier "$HOST"
    echo "Privates DNS: $HOST"
    ;;
  dns-off)
    "${A[@]}" shell settings put global private_dns_mode opportunistic
    "${A[@]}" shell settings delete global private_dns_specifier >/dev/null || true
    echo "Privates DNS zurückgesetzt (automatisch)."
    ;;
  home)
    installed de.klar.tvlauncher || { echo "Erst '$0 $IP install' ausführen."; exit 1; }
    for p in "${STOCK_LAUNCHERS[@]}" "${STOCK_HELPERS[@]}"; do
      if installed "$p"; then "${A[@]}" shell pm disable-user --user 0 "$p" && echo "deaktiviert: $p"; fi
    done
    "${A[@]}" shell input keyevent KEYCODE_HOME
    echo "Fertig. Rückgängig: $0 $IP restore"
    ;;
  restore)
    for p in "${STOCK_LAUNCHERS[@]}" "${STOCK_HELPERS[@]}"; do
      if installed "$p"; then "${A[@]}" shell pm enable "$p" >/dev/null && echo "aktiviert: $p"; fi
    done
    ;;
  wallpaper)
    IMG="${3:-}"
    [[ -f "$IMG" ]] || { echo "Bilddatei fehlt: $0 $IP wallpaper ~/Downloads/bild.jpg"; exit 1; }
    EXT="${IMG##*.}"; EXT="$(echo "$EXT" | tr 'A-Z' 'a-z')"; [[ "$EXT" == "png" ]] || EXT="jpg"
    DIR=/sdcard/Android/data/de.klar.tvlauncher/files
    "${A[@]}" shell mkdir -p "$DIR"
    "${A[@]}" shell rm -f "$DIR/hintergrund.jpg" "$DIR/hintergrund.jpeg" "$DIR/hintergrund.png"
    "${A[@]}" push "$IMG" "$DIR/hintergrund.$EXT"
    "${A[@]}" shell input keyevent KEYCODE_HOME
    echo "Hintergrund gesetzt."
    ;;
  status)
    echo "Gerät: $("${A[@]}" shell getprop ro.product.model | tr -d '\r')  (Android-SDK $SDK)"
    echo "Privates DNS: $("${A[@]}" shell settings get global private_dns_mode) / $("${A[@]}" shell settings get global private_dns_specifier)"
    echo "Klar installiert: $(installed de.klar.tvlauncher && echo ja || echo nein)"
    echo "Deaktivierte Pakete:"; "${A[@]}" shell pm list packages -d | sed 's/^package:/  /'
    ;;
  *) echo "Unbekannter Befehl: $CMD"; exit 1 ;;
esac
