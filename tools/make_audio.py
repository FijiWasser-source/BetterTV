#!/usr/bin/env python3
"""Erzeugt die Klänge für Klar komplett selbst (keine fremden Aufnahmen, keine Samples).

  - lofi_loop.wav : entspannter Lo-Fi-Loop, 8 Takte, ~76 BPM, nahtlos wiederholbar
  - sfx_jump.wav, sfx_star.wav, sfx_bump.wav : kurze Retro-Effekte für das Jump and Run

Nur Python-Standardbibliothek. Aufruf: python3 tools/make_audio.py <Zielordner>
"""
import math, random, struct, sys, wave

SR = 22050
random.seed(76)

def write(path, samples, gain=1.0):
    peak = max(1e-9, max(abs(s) for s in samples))
    k = gain / peak
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * k)) * 32000)) for s in samples))

def note(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)

# ---------------- Lo-Fi-Loop ----------------
BPM = 76
BEAT = 60 / BPM
BARS = 8
LEN = int(BARS * 4 * BEAT * SR)
buf = [0.0] * (LEN + int(4 * BEAT * SR))  # Platz für ausklingende Töne, wird am Ende zurückgefaltet

def add(start_s, dur_s, fn):
    i0 = int(start_s * SR)
    n = int(dur_s * SR)
    for i in range(n):
        j = i0 + i
        if j >= len(buf): break
        buf[j] += fn(i / SR)

def epiano(freq, vel):
    # weiches E-Piano: Sinus + etwas Oberton, leichte Verstimmung, sanftes Ausklingen
    def f(t):
        env = min(1, t / 0.012) * math.exp(-t * 1.6)
        wob = 1 + 0.0025 * math.sin(2 * math.pi * 0.35 * t)        # Band-"Leiern"
        p = 2 * math.pi * freq * wob * t
        return vel * env * (math.sin(p) + 0.18 * math.sin(2 * p) + 0.5 * math.sin(p * 1.003))
    return f

def bass(freq, vel):
    def f(t):
        env = min(1, t / 0.02) * math.exp(-t * 2.2)
        return vel * env * math.sin(2 * math.pi * freq * t)
    return f

def kick(vel):
    def f(t):
        fr = 45 + 70 * math.exp(-t * 30)
        return vel * math.exp(-t * 9) * math.sin(2 * math.pi * fr * t)
    return f

class Noise:
    def __init__(self, lp): self.y = 0; self.lp = lp
    def __call__(self):
        self.y += self.lp * (random.uniform(-1, 1) - self.y); return self.y

def snare(vel):
    n = Noise(0.35)
    def f(t):
        return vel * math.exp(-t * 18) * (n() * 1.6 + 0.25 * math.sin(2 * math.pi * 190 * t))
    return f

def hat(vel):
    prev = [0.0]
    def f(t):
        x = random.uniform(-1, 1); y = x - prev[0]; prev[0] = x   # Hochpass -> "tss"
        return vel * math.exp(-t * 60) * y
    return f

# Akkordfolge (eigene Voicings, gängige Jazz-/Lo-Fi-Harmonik): Fmaj9 – Em9 – Dm9 – Cmaj9, zweimal
chords = [
    (41, [57, 60, 64, 67]),   # F  : A C E G
    (40, [55, 59, 62, 66]),   # Em : G B D F#
    (38, [53, 57, 60, 64]),   # Dm : F A C E
    (36, [52, 55, 59, 62]),   # C  : E G B D
]
swing = 0.09 * BEAT
for bar in range(BARS):
    root, voicing = chords[bar % 4]
    t0 = bar * 4 * BEAT
    # E-Piano: Schlag 1 und auf "2 und", leicht versetzt angeschlagen
    for hit, vel in ((0.0, 0.16), (1.5 * BEAT + swing, 0.11)):
        for k, m in enumerate(voicing):
            add(t0 + hit + k * 0.012, 2.6, epiano(note(m), vel))
    # kleine Melodie-Tupfer in jedem zweiten Takt
    if bar % 2 == 1:
        top = voicing[-1] + 12
        for step, m in ((2.0, top), (2.5, top - 3), (3.0, top - 5)):
            add(t0 + step * BEAT + (swing if step % 1 else 0), 0.9, epiano(note(m), 0.07))
    # Bass
    add(t0, 1.6 * BEAT, bass(note(root), 0.55))
    add(t0 + 2.5 * BEAT + swing, 1.2 * BEAT, bass(note(root + 7), 0.4))
    # Schlagzeug (entspannt, geswingt)
    add(t0, 0.5, kick(0.9))
    add(t0 + 2.5 * BEAT + swing, 0.5, kick(0.7))
    add(t0 + 1 * BEAT, 0.3, snare(0.32))
    add(t0 + 3 * BEAT, 0.3, snare(0.32))
    for e in range(8):
        add(t0 + e * 0.5 * BEAT + (swing if e % 2 else 0), 0.08, hat(0.12 if e % 2 else 0.08))

# Ausklingendes vom Ende an den Anfang falten -> nahtloser Loop
for i in range(LEN, len(buf)):
    buf[i - LEN] += buf[i]
loop = buf[:LEN]

# Vinyl-Knistern + leichtes Rauschen, dann warm filtern (Tiefpass)
for i in range(LEN):
    loop[i] += random.uniform(-1, 1) * 0.006
    if random.random() < 0.0006:
        loop[i] += random.choice((-1, 1)) * random.uniform(0.05, 0.12)
y = 0.0
for _ in range(2):  # zweimal = sanfterer Tiefpass (~3,5 kHz)
    for i in range(LEN):
        y += 0.5 * (loop[i] - y); loop[i] = y

out = sys.argv[1] if len(sys.argv) > 1 else "."
write(out + "/lofi_loop.wav", loop, 0.5)

# ---------------- Soundeffekte ----------------
def sfx_jump():
    n = int(0.14 * SR); s = []
    ph = 0
    for i in range(n):
        t = i / SR
        f = 380 + 520 * (t / 0.14)
        ph += f / SR
        sq = 1 if (ph % 1) < 0.5 else -1
        s.append(sq * min(1, t / 0.004) * math.exp(-t * 18) * 0.6)
    return s

def sfx_star():
    s = []
    for f0, start in ((1046.5, 0.0), (1568.0, 0.07)):
        pass
    n = int(0.45 * SR)
    for i in range(n):
        t = i / SR
        v = 0
        for f0, st in ((1046.5, 0.0), (1568.0, 0.07)):
            if t >= st:
                tt = t - st
                tri = 2 / math.pi * math.asin(math.sin(2 * math.pi * f0 * tt))
                v += tri * math.exp(-tt * 9)
        s.append(v * 0.5)
    return s

def sfx_bump():
    n = int(0.22 * SR); s = []; nz = Noise(0.2)
    for i in range(n):
        t = i / SR
        fr = 70 + 110 * math.exp(-t * 25)
        s.append((math.sin(2 * math.pi * fr * t) * 0.9 + nz() * 0.6) * math.exp(-t * 14))
    return s

def sfx_talk():
    # ein kurzer "Plapper"-Laut; beim Sprechen mit wechselnder Tonhöhe abgespielt
    n = int(0.07 * SR); s = []; ph = 0
    for i in range(n):
        t = i / SR
        f = 520 * (1 + 0.15 * math.sin(2 * math.pi * 30 * t))
        ph += f / SR
        v = (1 if (ph % 1) < 0.35 else -1) * 0.5 + math.sin(2 * math.pi * ph) * 0.5
        s.append(v * min(1, t / 0.004) * math.exp(-t * 32))
    return s

def sfx_levelup():
    s = []; n = int(0.62 * SR)
    notes = ((523.3, 0.0), (659.3, 0.11), (784.0, 0.22), (1046.5, 0.33))
    for i in range(n):
        t = i / SR; v = 0
        for f0, st in notes:
            if t >= st:
                tt = t - st; ph = f0 * tt
                v += (1 if (ph % 1) < 0.5 else -1) * 0.35 * math.exp(-tt * 7) * min(1, tt / 0.004)
        s.append(v)
    return s

def sfx_land():
    n = int(0.09 * SR); s = []; nz = Noise(0.3)
    for i in range(n):
        t = i / SR
        s.append((nz() * 0.8 + math.sin(2 * math.pi * 140 * t) * 0.4) * math.exp(-t * 40) * min(1, t / 0.002))
    return s

def sfx_engine():
    # leises, tiefes Brummen eines kleinen Motors; genau 0,5 s -> nahtlos als Schleife
    n = int(0.5 * SR); s = []; nz = Noise(0.05)
    fire = 24.0  # Zündungen pro Sekunde (ganzzahlig in 0,5 s -> nahtlos)
    for i in range(n):
        t = i / SR
        ph = (t * fire) % 1
        pulse = math.exp(-ph * 9)                       # "Tuckern"
        tone = math.sin(2 * math.pi * 48 * t) * 0.6 + math.sin(2 * math.pi * 96 * t) * 0.25
        s.append((tone * (0.55 + 0.45 * pulse) + nz() * 0.5 * pulse))
    # Anfang/Ende angleichen
    fade = int(0.01 * SR)
    for i in range(fade):
        k = i / fade; s[i] = s[i] * k + s[n - fade + i] * (1 - k)
    return s[:n - fade]

write(out + "/sfx_engine.wav", sfx_engine(), 0.3)
write(out + "/sfx_jump.wav", sfx_jump(), 0.35)
write(out + "/sfx_talk.wav", sfx_talk(), 0.3)
write(out + "/sfx_levelup.wav", sfx_levelup(), 0.4)
write(out + "/sfx_land.wav", sfx_land(), 0.25)
write(out + "/sfx_star.wav", sfx_star(), 0.5)
write(out + "/sfx_bump.wav", sfx_bump(), 0.45)
print("fertig:", out)
