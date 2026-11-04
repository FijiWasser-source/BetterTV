#!/usr/bin/env python3
"""Findet Fernseher (v. a. Sony BRAVIA / Android TV) im eigenen Netzwerk.

Nur Python-Standardbibliothek. Auf dem Computer im selben WLAN/LAN wie der TV ausführen:

    python3 tools/find-tv.py            # Liste aller gefundenen Geräte
    python3 tools/find-tv.py --ip-only  # nur die IP des ersten Sony-TVs (für Skripte)

Vorgehen:
  1. SSDP/UPnP-Rundruf (so finden sich auch Handy-Apps und Chromecast). Der TV muss an sein.
  2. Falls nichts kommt: kurzer Scan des eigenen /24-Netzes nach der Sony-Schnittstelle
     (HTTP /sony/system) und dem ADB-Port 5555.
"""
import argparse
import concurrent.futures as cf
import ipaddress
import json
import re
import socket
import sys
import urllib.request
import xml.etree.ElementTree as ET

SSDP_ADDR = ("239.255.255.250", 1900)
SEARCH_TARGETS = ["ssdp:all", "urn:schemas-sony-com:service:IRCC:1", "urn:dial-multiscreen-org:service:dial:1"]


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # sendet nichts, ermittelt nur die eigene Adresse
        return s.getsockname()[0]
    except OSError:
        return None
    finally:
        s.close()


def ssdp_search(timeout=3.0):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
    sock.settimeout(timeout)
    for st in SEARCH_TARGETS:
        msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"
               'MAN: "ssdp:discover"\r\nMX: 2\r\nST: %s\r\n\r\n' % st)
        try:
            sock.sendto(msg.encode(), SSDP_ADDR)
        except OSError:
            pass
    found = {}
    while True:
        try:
            data, (ip, _) = sock.recvfrom(65507)
        except (socket.timeout, OSError):
            break
        text = data.decode("utf-8", "replace")
        m = re.search(r"^location:\s*(\S+)", text, re.I | re.M)
        server = re.search(r"^server:\s*(.+)$", text, re.I | re.M)
        entry = found.setdefault(ip, {"ip": ip, "locations": set(), "server": ""})
        if m:
            entry["locations"].add(m.group(1).strip())
        if server and not entry["server"]:
            entry["server"] = server.group(1).strip()
    sock.close()
    return list(found.values())


def describe(location):
    """Liest Hersteller/Modell aus der UPnP-Gerätebeschreibung."""
    try:
        with urllib.request.urlopen(location, timeout=2) as r:
            root = ET.fromstring(r.read())
    except Exception:
        return {}
    info = {}
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag in ("friendlyName", "manufacturer", "modelName") and el.text and tag not in info:
            info[tag] = el.text.strip()
    return info


def sony_api(ip):
    """Sony BRAVIA REST-Schnittstelle (ohne Anmeldung abrufbar)."""
    body = json.dumps({"method": "getInterfaceInformation", "id": 1, "params": [], "version": "1.0"}).encode()
    req = urllib.request.Request("http://%s/sony/system" % ip, data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=1.5) as r:
            result = json.loads(r.read()).get("result", [{}])[0]
            return {"manufacturer": "Sony", "modelName": result.get("modelName", "BRAVIA"),
                    "friendlyName": result.get("productName", "BRAVIA")}
    except Exception:
        return {}


def port_open(ip, port, timeout=0.4):
    try:
        with socket.create_connection((ip, port), timeout=timeout):
            return True
    except OSError:
        return False


def scan_subnet(own_ip):
    net = ipaddress.ip_network(own_ip + "/24", strict=False)
    hosts = [str(h) for h in net.hosts() if str(h) != own_ip]

    def probe(ip):
        adb = port_open(ip, 5555)
        info = sony_api(ip) if port_open(ip, 80) else {}
        if info or adb:
            return {"ip": ip, **info, "adb": adb}
        return None

    with cf.ThreadPoolExecutor(max_workers=64) as pool:
        return [r for r in pool.map(probe, hosts) if r]


def is_sony(d):
    blob = " ".join(str(d.get(k, "")) for k in ("manufacturer", "modelName", "friendlyName", "server")).lower()
    return "sony" in blob or "bravia" in blob or blob.strip().startswith("kd-")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--ip-only", action="store_true", help="nur die IP des ersten Sony-TVs ausgeben")
    ap.add_argument("--scan", action="store_true", help="immer auch das Subnetz scannen")
    args = ap.parse_args()
    log = (lambda *a: None) if args.ip_only else (lambda *a: print(*a, file=sys.stderr))

    own = local_ip()
    log("Eigene Adresse: %s – suche per SSDP …" % (own or "unbekannt"))
    devices = []
    for d in ssdp_search():
        info = {}
        for loc in sorted(d["locations"]):
            info = describe(loc)
            if info.get("manufacturer"):
                break
        d.update(info)
        if is_sony(d) and "modelName" not in d:
            d.update(sony_api(d["ip"]))
        d["adb"] = port_open(d["ip"], 5555)
        devices.append(d)

    if args.scan or not any(is_sony(d) for d in devices):
        if own:
            log("Durchsuche %s/24 nach Sony-Schnittstelle und ADB-Port …" % own)
            known = {d["ip"] for d in devices}
            devices += [d for d in scan_subnet(own) if d["ip"] not in known]

    devices.sort(key=lambda d: (not is_sony(d), ipaddress.ip_address(d["ip"])))
    sony = [d for d in devices if is_sony(d)]

    if args.ip_only:
        if sony:
            print(sony[0]["ip"])
            return 0
        return 1

    if not devices:
        print("Nichts gefunden. Ist der TV an und im selben Netz? (Gast-WLAN trennt Geräte oft.)")
        return 1
    for d in devices:
        mark = "TV  " if is_sony(d) else "    "
        name = d.get("friendlyName") or d.get("server") or "?"
        model = d.get("modelName", "")
        adb = "ADB offen" if d.get("adb") else "ADB zu"
        print("%s%-15s  %-28s %-16s %s" % (mark, d["ip"], name[:28], model[:16], adb))
    if sony:
        tv = sony[0]
        print("\nSony-TV: %s (%s)" % (tv["ip"], tv.get("modelName", "BRAVIA")))
        if not tv.get("adb"):
            print("ADB-Port 5555 ist zu: am TV Entwickleroptionen → USB-Debugging einschalten.")
        print("Weiter mit:  ./tools/tv-setup.sh %s install" % tv["ip"])
        return 0
    print("\nKein Sony-TV erkannt. Die IP steht auch am TV unter Einstellungen → Netzwerk.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
