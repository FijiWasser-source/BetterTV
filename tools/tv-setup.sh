#!/usr/bin/env bash
# Richtet den Klar-Launcher und einen werbeblockenden DNS auf einem Android-/Google-TV ein.
# Voraussetzung: "Entwickleroptionen" + "USB-/Netzwerk-Debugging" am TV aktiv, adb installiert.
#
#   ./tools/tv-setup.sh <TV-IP> install            APK installieren
#   ./tools/tv-setup.sh <TV-IP> dns [hostname]     Privates DNS setzen (Standard: dns.adguard-dns.com)
#   ./tools/tv-setup.sh <TV-IP> dns-off            Privates DNS zurücksetzen
#   ./tools/tv-setup.sh <TV-IP> home               Original-Startseite deaktivieren -> Klar wird Home
#   ./tools/tv-setup.sh <TV-IP> restore            Original-Startseite wieder aktivieren
#   ./tools/tv-setup.sh <TV-IP> status             Aktuellen Zustand anzeigen
set -euo pipefail

IP="${1:-}"; CMD="${2:-}"
if [[ -z "$IP" || -z "$CMD" ]]; then sed -n '2,12p' "$0"; exit 1; fi

APK="$(cd "$(dirname "$0")/.." && pwd)/tv-launcher/app/build/outputs/apk/debug/app-debug.apk"
# Bekannte Original-Startseiten (Google TV, älteres Android TV) und deren Einrichtungs-Assistent.
STOCK_LAUNCHERS=(com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher)
STOCK_HELPERS=(com.google.android.tungsten.setupwraith)

adb connect "$IP:5555" >/dev/null
A=(adb -s "$IP:5555")
installed() { "${A[@]}" shell pm list packages "$1" | grep -qx "package:$1"; }

case "$CMD" in
  install)
    [[ -f "$APK" ]] || { echo "APK fehlt: erst 'cd tv-launcher && ./gradlew assembleDebug' ausführen"; exit 1; }
    "${A[@]}" install -r "$APK"
    ;;
  dns)
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
  status)
    echo "Privates DNS: $("${A[@]}" shell settings get global private_dns_mode) / $("${A[@]}" shell settings get global private_dns_specifier)"
    echo "Klar installiert: $(installed de.klar.tvlauncher && echo ja || echo nein)"
    echo "Deaktivierte Pakete:"; "${A[@]}" shell pm list packages -d | sed 's/^package:/  /'
    ;;
  *) echo "Unbekannter Befehl: $CMD"; exit 1 ;;
esac
