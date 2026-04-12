"""Build a 30-second promo MP4 from the framed phone screenshots.

Output: play-store/metadata/android/en-US/images/promoVideo/promo.mp4

Why 1920x1080? Play Store's promo video slot takes a YouTube URL, and
YouTube's standard player is 16:9. We pad each vertical 1080x1920
screenshot onto a branded 1920x1080 canvas with a headline card so it
reads cleanly both in-feed and in the Play listing.

Requires ffmpeg on PATH and the screenshots already generated via
build_screenshots.py.
"""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[2]
META = REPO_ROOT / "play-store" / "metadata" / "android" / "en-US" / "images"
FRAMES_DIR = REPO_ROOT / "play-store" / "scripts" / ".video_frames"
OUT_DIR = META / "promoVideo"
OUT_PATH = OUT_DIR / "promo.mp4"

VIDEO_W, VIDEO_H = 1920, 1080
FPS = 30

INDIGO_DEEP = (26, 35, 126)
INDIGO_LIGHT = (92, 107, 192)
WHITE = (255, 255, 255)
MUTED = (210, 218, 240)


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = (
        ["C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf"]
        if bold
        else ["C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"]
    )
    for c in candidates:
        try:
            return ImageFont.truetype(c, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def gradient_bg(size: tuple[int, int]) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size, INDIGO_DEEP)
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        r = int(INDIGO_LIGHT[0] * (1 - t) + INDIGO_DEEP[0] * t)
        g = int(INDIGO_LIGHT[1] * (1 - t) + INDIGO_DEEP[1] * t)
        b = int(INDIGO_LIGHT[2] * (1 - t) + INDIGO_DEEP[2] * t)
        for x in range(w):
            px[x, y] = (r, g, b)
    return img


def intro_card() -> Image.Image:
    bg = gradient_bg((VIDEO_W, VIDEO_H)).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    # Soft diagonal highlight
    ov = Image.new("RGBA", (VIDEO_W, VIDEO_H), (0, 0, 0, 0))
    ImageDraw.Draw(ov).polygon(
        [(0, VIDEO_H), (VIDEO_W, 0), (VIDEO_W, int(VIDEO_H * 0.25)),
         (0, int(VIDEO_H * 1.2))],
        fill=(255, 255, 255, 18),
    )
    bg = Image.alpha_composite(bg, ov)
    draw = ImageDraw.Draw(bg)

    # Paste the 512 icon (alpha-safe variant with rounded square)
    icon_path = META / "icon" / "icon.png"
    icon = Image.open(icon_path).convert("RGBA")
    icon_size = 360
    icon = icon.resize((icon_size, icon_size), Image.LANCZOS)
    # Round the icon corners for the promo card
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, icon_size - 1, icon_size - 1], radius=72, fill=255
    )
    icon.putalpha(mask)
    bg.paste(icon, ((VIDEO_W - icon_size) // 2, 180), icon)

    title_font = load_font(110, bold=True)
    sub_font = load_font(44)
    title = "Pocket Assistant"
    sub = "Local-first AI organizer for Android"

    def centered(text: str, font: ImageFont.FreeTypeFont, y: int,
                 color: tuple[int, int, int]) -> None:
        w = draw.textlength(text, font=font)
        x = (VIDEO_W - w) // 2
        draw.text((x + 3, y + 3), text, font=font, fill=(0, 0, 0, 120))
        draw.text((x, y), text, font=font, fill=color)

    centered(title, title_font, 600, WHITE)
    centered(sub, sub_font, 760, MUTED)
    return bg.convert("RGB")


def outro_card() -> Image.Image:
    bg = gradient_bg((VIDEO_W, VIDEO_H)).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    ov = Image.new("RGBA", (VIDEO_W, VIDEO_H), (0, 0, 0, 0))
    ImageDraw.Draw(ov).polygon(
        [(0, VIDEO_H), (VIDEO_W, 0), (VIDEO_W, int(VIDEO_H * 0.25)),
         (0, int(VIDEO_H * 1.2))],
        fill=(255, 255, 255, 18),
    )
    bg = Image.alpha_composite(bg, ov)
    draw = ImageDraw.Draw(bg)

    title_font = load_font(96, bold=True)
    sub_font = load_font(40)

    def centered(text: str, font: ImageFont.FreeTypeFont, y: int,
                 color: tuple[int, int, int]) -> None:
        w = draw.textlength(text, font=font)
        x = (VIDEO_W - w) // 2
        draw.text((x + 3, y + 3), text, font=font, fill=(0, 0, 0, 120))
        draw.text((x, y), text, font=font, fill=color)

    centered("Private. Offline. Free.", title_font, 360, WHITE)
    centered("github.com/chartmann1590/Pocket-Assistant", sub_font, 520, MUTED)
    centered("Get it on Google Play", sub_font, 600, WHITE)
    return bg.convert("RGB")


def slide(src_path: Path, headline: str) -> Image.Image:
    bg = gradient_bg((VIDEO_W, VIDEO_H)).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    ov = Image.new("RGBA", (VIDEO_W, VIDEO_H), (0, 0, 0, 0))
    ImageDraw.Draw(ov).polygon(
        [(0, VIDEO_H), (VIDEO_W, 0), (VIDEO_W, int(VIDEO_H * 0.22)),
         (0, int(VIDEO_H * 1.1))],
        fill=(255, 255, 255, 16),
    )
    bg = Image.alpha_composite(bg, ov)
    draw = ImageDraw.Draw(bg)

    # Load the already-framed 1080x1920 screenshot and scale to fit the right column
    src = Image.open(src_path).convert("RGBA")
    target_h = VIDEO_H - 120
    scale = target_h / src.size[1]
    new_w = int(src.size[0] * scale)
    new_h = int(src.size[1] * scale)
    shot = src.resize((new_w, new_h), Image.LANCZOS)

    shot_x = VIDEO_W - new_w - 90
    shot_y = (VIDEO_H - new_h) // 2

    # Drop shadow for the shot
    shadow = Image.new("RGBA", (VIDEO_W, VIDEO_H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rectangle([shot_x + 18, shot_y + 24, shot_x + new_w + 18, shot_y + new_h + 24],
                 fill=(0, 0, 0, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=30))
    bg = Image.alpha_composite(bg, shadow)
    bg.paste(shot, (shot_x, shot_y), shot)

    draw = ImageDraw.Draw(bg)

    # Headline on the left, large
    title_font = load_font(96, bold=True)
    text_x = 96
    text_y = 260
    max_w = shot_x - text_x - 60
    words = headline.split()
    lines: list[str] = []
    cur = ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=title_font) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)

    for line in lines:
        draw.text((text_x + 3, text_y + 3), line, font=title_font,
                  fill=(0, 0, 0, 120))
        draw.text((text_x, text_y), line, font=title_font, fill=WHITE)
        text_y += title_font.size + 10

    return bg.convert("RGB")


SLIDES = [
    ("01_home.png", "Your pocket, organized."),
    ("02_models.png", "Pick your local LLM."),
    ("03_assistant.png", "Ask anything you've saved."),
    ("04_local_ai.png", "Runs on your device."),
    ("05_tasks.png", "Tasks and reminders."),
    ("06_classified.png", "Bills, messages, notes."),
]

# Timing (seconds). Intro 3, slides 3.5 each = 21, outro 3 → total 27s
INTRO_S = 3.0
SLIDE_S = 3.5
OUTRO_S = 3.0


def main() -> None:
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg not found on PATH")

    FRAMES_DIR.mkdir(parents=True, exist_ok=True)
    for p in FRAMES_DIR.glob("*.png"):
        p.unlink()

    print("Rendering frames...")
    intro_card().save(FRAMES_DIR / "00_intro.png")
    for i, (name, headline) in enumerate(SLIDES, start=1):
        src = META / "phoneScreenshots" / name
        slide(src, headline).save(FRAMES_DIR / f"{i:02d}_{name}")
    outro_card().save(FRAMES_DIR / "99_outro.png")

    # Concat list for ffmpeg
    concat_list = FRAMES_DIR / "concat.txt"
    with concat_list.open("w", encoding="utf-8") as f:
        f.write(f"file '{(FRAMES_DIR / '00_intro.png').as_posix()}'\n")
        f.write(f"duration {INTRO_S}\n")
        for i, (name, _) in enumerate(SLIDES, start=1):
            path = FRAMES_DIR / f"{i:02d}_{name}"
            f.write(f"file '{path.as_posix()}'\n")
            f.write(f"duration {SLIDE_S}\n")
        f.write(f"file '{(FRAMES_DIR / '99_outro.png').as_posix()}'\n")
        f.write(f"duration {OUTRO_S}\n")
        # ffmpeg concat demuxer requires the last file to be listed twice
        f.write(f"file '{(FRAMES_DIR / '99_outro.png').as_posix()}'\n")

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print("Encoding with ffmpeg...")
    cmd = [
        "ffmpeg",
        "-y",
        "-f", "concat",
        "-safe", "0",
        "-i", str(concat_list),
        "-vf", f"fps={FPS},format=yuv420p",
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        "-preset", "medium",
        "-crf", "20",
        "-movflags", "+faststart",
        str(OUT_PATH),
    ]
    subprocess.run(cmd, check=True)
    print(f"wrote {OUT_PATH.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
