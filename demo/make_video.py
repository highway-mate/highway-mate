"""Burn the demo captions into the screen recordings, add the alert sounds, and join the parts into one video.

usage: python -I make_video.py <out_dir> <final.mp4> part1 [part2 ...]
Needs Pillow, numpy and imageio-ffmpeg (bundled ffmpeg binary).

The emulator screen recording has no audio, so the app's alert sounds are rebuilt here:
demo/sounds/<part>.tsv lists when each heads-up card appears (seconds in the recording), the alert level,
and the spoken sentence. Chimes follow Voice.kt (caution = one chime, urgent = three, info = soft two-note);
speech uses the Windows Korean voice (Microsoft Heami), standing in for the pre-recorded clips the product plans.
"""
import os
import subprocess
import sys
import wave

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT, FINAL, PARTS = sys.argv[1], sys.argv[2], sys.argv[3:]
FF = imageio_ffmpeg.get_ffmpeg_exe()
W, H = 1920, 1080
FONT = "C:/Windows/Fonts/malgunbd.ttf"
EMOJI = "C:/Windows/Fonts/seguiemj.ttf"
LEAD = 1.0  # screenrecord starts about 1 s before the caption clock
HERE = os.path.dirname(os.path.abspath(__file__))
SR = 48000


def tone(freqs, dur, amp, decay):
    t = np.arange(int(SR * dur)) / SR
    wave_ = sum(a * np.sin(2 * np.pi * f * t) for f, a in freqs)
    env = np.minimum(t / 0.006, 1.0) * np.exp(-t / decay)
    return amp * wave_ * env


def chime(kind: str) -> np.ndarray:
    def bell(f, amp, dur=0.9, decay=0.22):
        return tone([(f, 1.0), (f * 2, 0.25), (f * 3, 0.08)], dur, amp, decay)

    if kind == "caution":   # one clear chime
        return bell(1046.5, 0.42)
    if kind == "urgent":    # three quick, higher chimes
        out = np.zeros(int(SR * 1.2))
        for i in range(3):
            b = bell(1318.5, 0.45, 0.5, 0.1)
            s = int(SR * 0.26 * i)
            out[s:s + len(b)] += b
        return out
    if kind == "info":      # soft ascending two-note
        a, b = bell(784.0, 0.26, 0.6, 0.16), bell(1046.5, 0.26, 0.9, 0.22)
        out = np.zeros(int(SR * 0.18) + len(b))
        out[:len(a)] += a
        out[int(SR * 0.18):] += b
        return out
    if kind == "tap":       # short button tick
        return tone([(2200, 1.0), (3300, 0.4)], 0.05, 0.25, 0.012)
    raise ValueError(kind)


def speech(text: str, path: str) -> np.ndarray:
    if not os.path.exists(path):
        subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                        os.path.join(HERE, "sounds", "tts.ps1"), "-text", text, "-out", os.path.abspath(path),
                        "-rate", "2"], check=True, capture_output=True)
    with wave.open(path) as w:
        sr = w.getframerate()
        a = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float64) / 32768
    a = np.interp(np.arange(int(len(a) * SR / sr)) * sr / SR, np.arange(len(a)), a)  # to 48 kHz
    nz = np.where(np.abs(a) > 0.01)[0]
    a = a[nz[0]:nz[-1] + 1] if len(nz) else a  # trim the engine's leading/trailing silence
    return a * (0.7 / max(np.abs(a).max(), 1e-6))


def soundtrack(part: str, duration: float) -> str | None:
    cues = os.path.join(HERE, "sounds", part + ".tsv")
    if not os.path.exists(cues):
        return None
    mix = np.zeros(int(SR * (duration + 10)))
    busy_until = 0.0  # never let two spoken alerts overlap (the app keeps them apart too)
    with open(cues, encoding="utf8") as f:
        rows = [ln.rstrip("\n").split("\t") for ln in f if ln.strip() and not ln.startswith("#")]
    for k, (t, kind, *rest) in enumerate(rows):
        text = rest[0] if rest else ""
        start = max(float(t), busy_until + 0.3) if text else float(t)
        c = chime(kind)
        s = int(SR * start)
        mix[s:s + len(c)] += c
        if text:
            v = speech(text, os.path.join(OUT, f"{part}_voice{k}.wav"))
            vs = s + int(SR * (0.55 if kind == "info" else 0.45))
            mix[vs:vs + len(v)] += v
            busy_until = (vs + len(v)) / SR
            if start != float(t):
                print(f"  {part}: cue at {t}s moved to {start:.2f}s so voices do not overlap")
    mix = mix[:int(SR * duration)]
    mix = np.clip(mix, -0.98, 0.98)
    dst = os.path.join(OUT, part + "_audio.wav")
    with wave.open(dst, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((mix * 32767).astype(np.int16).tobytes())
    return dst


def video_duration(path: str) -> float:
    r = subprocess.run([FF, "-i", path], capture_output=True, text=True, encoding="utf8", errors="replace")
    hms = r.stderr.split("Duration: ")[1].split(",")[0]
    h, m, s = hms.split(":")
    return int(h) * 3600 + int(m) * 60 + float(s)


def caption_png(text: str, path: str):
    font = ImageFont.truetype(FONT, 40)
    pad_x, pad_y = 36, 20
    tmp = Image.new("RGBA", (10, 10))
    box = ImageDraw.Draw(tmp).textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    img = Image.new("RGBA", (tw + pad_x * 2, th + pad_y * 2 + 8), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius=18, fill=(8, 12, 18, 225))
    dr.text((pad_x, pad_y - box[1]), text, font=font, fill=(255, 255, 255, 255), embedded_color=True)
    img.save(path)


def burn(part: str) -> str:
    src = os.path.join(OUT, part + ".mp4")
    rows = []
    with open(os.path.join(OUT, part + ".captions.tsv"), encoding="utf8") as f:
        for line in f:
            t, _, text = line.rstrip("\n").partition("\t")
            rows.append((float(t) + LEAD, text))
    inputs, filters, last = ["-i", src], [], "[0:v]"
    k = 0
    for i, (t, text) in enumerate(rows):
        if not text:
            continue
        end = rows[i + 1][0] if i + 1 < len(rows) else t + 6
        png = os.path.join(OUT, f"{part}_cap{i}.png")
        caption_png(text.replace("🟡", "●"), png)
        inputs += ["-i", png]
        k += 1
        nxt = f"[v{k}]"
        filters.append(f"{last}[{k}:v]overlay=x=(W-w)/2:y=H-h-150:enable='between(t,{t:.2f},{end:.2f})'{nxt}")
        last = nxt
    dst = os.path.join(OUT, part + "_cap.mp4")
    cmd = [FF, "-y", *inputs, "-filter_complex", ";".join(filters) or "null", "-map", last if filters else "0:v",
           "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-r", "30", dst]
    subprocess.run(cmd, check=True, capture_output=True)
    return dst


def add_audio(part: str, video: str) -> str:
    dur = video_duration(video)
    audio = soundtrack(part, dur)
    src = ["-i", audio] if audio else ["-f", "lavfi", "-t", f"{dur:.2f}", "-i", f"anullsrc=r={SR}:cl=mono"]
    dst = os.path.join(OUT, part + "_av.mp4")
    subprocess.run([FF, "-y", "-i", video, *src, "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                    "-c:a", "aac", "-b:a", "160k", "-ar", str(SR), "-ac", "2", "-shortest", dst],
                   check=True, capture_output=True)
    return dst


def main():
    clips = [add_audio(p, burn(p)) for p in PARTS]
    lst = os.path.join(OUT, "concat.txt")
    with open(lst, "w", encoding="utf8") as f:
        for c in clips:
            f.write(f"file '{os.path.abspath(c)}'\n")
    subprocess.run([FF, "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", FINAL], check=True, capture_output=True)
    print("wrote", FINAL)


main()
