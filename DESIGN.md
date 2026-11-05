# BetterTV – Design

Übernommen aus der App (Design „Abend“). Gilt für die App, die Handy-Fernbedienung und die Website.

## Richtung
Ruhig, dunkel, verspielt im Detail: grauer Grund mit Punkteraster wie Dot-Grid-Papier,
große runde Schrift, Pixel-Art-Figuren als einzige Illustration. Keine Fotos, keine Verläufe
in Knallfarben, keine Glas-Effekte auf der Website.

## Farben
| Token | Wert | Rolle |
|---|---|---|
| `--bg` | `#2C2F34` | Seitenhintergrund |
| `--dot` | `rgba(255,255,255,.08)` | Punkteraster (Abstand 24 px, Punkt 1,5 px) |
| `--surface` | `#212328` | Karten, Felder (dunkles Glas) |
| `--key-hi` / `--key-lo` | `#4A4E56` / `#3A3D44` | Tasten-Verlauf (wie Handy-Fernbedienung) |
| `--text` | `#FFFFFF` | Text |
| `--muted` | `#A9ADB5` | Nebentext, Beschriftungen |
| `--blue` | `#8FB4FF` | Begrüßung, Datum, Überschrift-Akzent |
| `--orange` | `#FF8A1F` | Hauptaktion, Leuchtrand, Schlusswort („heute?“) |
| `--orange-soft` | `#FFB566` | Wetter, kleine Akzente |
| `--ink` | `#1B2640` | Text auf hellen Flächen |

Nur ein dunkles Thema – die App ist bewusst dunkel (Fernseher im Abendlicht).

## Schrift
- Überschriften: **Nunito ExtraBold** (lokal eingebunden, OFL 1.1), eng (−0,01 em).
  Hero 46–72 px, Abschnitt 28–36 px.
- Fließtext: Systemschrift (`-apple-system, Segoe UI, Roboto, sans-serif`), 16–18 px, Zeilenhöhe 1,5.
- Pixel-Schrift (5×7) nur für Uhr/Zähler/Sprechblasen, nie für Fließtext.

## Form
- Radius: Karten 18 px, Tasten 14 px, Pillen voll rund.
- Leuchtrand: `1.5px solid rgba(255,138,31,.75)` + `0 0 10px rgba(255,138,31,.25)`;
  Fokus/aktiv: `0 0 0 2.5px #FF8A1F, 0 0 16px 3px rgba(255,138,31,.6)`.
- Abstände in 8er-Schritten; Seitenrand 16 px (Handy) bis 64 px (TV/Desktop).

## Pixel-Art
Figuren kommen aus denselben Raster-Daten wie in der App (`Mascot`, `SkyLayer`, `HelperLayer`):
Pixel (orange), Schluck (blau, lange Glieder), das verfolgende Auto (blau), Wolken, Vögel,
Vollmond. Gezeichnet mit `image-rendering: pixelated`, ganzzahlige Pixelgrößen.
Bewegung nur dezent und nie bei `prefers-reduced-motion`.
