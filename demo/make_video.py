"""Burn the demo captions into the screen recordings and join the parts into one video.

usage: python -I make_video.py <out_dir> <final.mp4> part1 [part2 ...]
Needs Pillow and imageio-ffmpeg (bundled ffmpeg binary).
"""
import os
import subprocess
import sys

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

OUT, FINAL, PARTS = sys.argv[1], sys.argv[2], sys.argv[3:]
FF = imageio_ffmpeg.get_ffmpeg_exe()
W, H = 1920, 1080
FONT = "C:/Windows/Fonts/malgunbd.ttf"
EMOJI = "C:/Windows/Fonts/seguiemj.ttf"
LEAD = 1.0  # screenrecord starts about 1 s before the caption clock


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


def main():
    clips = [burn(p) for p in PARTS]
    lst = os.path.join(OUT, "concat.txt")
    with open(lst, "w", encoding="utf8") as f:
        for c in clips:
            f.write(f"file '{os.path.abspath(c)}'\n")
    subprocess.run([FF, "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", FINAL], check=True, capture_output=True)
    print("wrote", FINAL)


main()
