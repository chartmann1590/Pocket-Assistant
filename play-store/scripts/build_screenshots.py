"""Frame device screenshots for the Play Store.

Each source capture is 1008x2244 (ratio ~2.23), which is slightly outside the
9:16 aspect ratio the Play Console prefers. We crop out the system status bar
(which shows the user's personal notifications), then place the shot on a
1080x1920 branded frame with a headline.

Output: play-store/metadata/android/en-US/images/phoneScreenshots/NN_*.png
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = REPO_ROOT / "play-store" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"
SCREENS = REPO_ROOT / "screenshots"

INDIGO_DEEP = (26, 35, 126)
INDIGO_LIGHT = (92, 107, 192)
WHITE = (255, 255, 255)
MUTED = (210, 218, 240)

FRAME_W, FRAME_H = 1080, 1920
STATUS_BAR_CROP = 140  # px of status bar to chop off the top of each source


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates_bold = [
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/seguisb.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
    ]
    candidates_reg = [
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for c in (candidates_bold if bold else candidates_reg):
        try:
            return ImageFont.truetype(c, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def gradient_bg(size: tuple[int, int]) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size, INDIGO_DEEP)
    top = INDIGO_LIGHT
    bot = INDIGO_DEEP
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        r = int(top[0] * (1 - t) + bot[0] * t)
        g = int(top[1] * (1 - t) + bot[1] * t)
        b = int(top[2] * (1 - t) + bot[2] * t)
        for x in range(w):
            px[x, y] = (r, g, b)
    return img


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1],
                                           radius=radius, fill=255)
    return mask


def wrap_text(text: str, font: ImageFont.FreeTypeFont, max_width: int,
              draw: ImageDraw.ImageDraw) -> list[str]:
    words = text.split()
    lines: list[str] = []
    cur = ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=font) <= max_width:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def frame(source_path: Path, headline: str, subline: str, out_path: Path) -> None:
    bg = gradient_bg((FRAME_W, FRAME_H)).convert("RGBA")

    # Decorative sparkles / soft diagonal like the feature graphic
    overlay = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon([(0, FRAME_H), (FRAME_W, 0),
                (FRAME_W, int(FRAME_H * 0.18)), (0, int(FRAME_H * 1.15))],
               fill=(255, 255, 255, 16))
    bg = Image.alpha_composite(bg, overlay)

    draw = ImageDraw.Draw(bg)

    # Headline + subline
    title_font = load_font(74, bold=True)
    sub_font = load_font(36)

    margin = 72
    text_top = 96
    max_text_w = FRAME_W - margin * 2

    title_lines = wrap_text(headline, title_font, max_text_w, draw)
    y = text_top
    for line in title_lines:
        # shadow
        draw.text((margin + 3, y + 3), line, font=title_font, fill=(0, 0, 0, 110))
        draw.text((margin, y), line, font=title_font, fill=WHITE)
        y += title_font.size + 8

    y += 4
    for sub in wrap_text(subline, sub_font, max_text_w, draw):
        draw.text((margin, y), sub, font=sub_font, fill=MUTED)
        y += sub_font.size + 4

    # Load + crop source (remove status bar with personal notifications)
    src = Image.open(source_path).convert("RGBA")
    sw, sh = src.size
    src = src.crop((0, STATUS_BAR_CROP, sw, sh))
    sw, sh = src.size

    # Scale to fit under the header, leaving bottom breathing room
    header_bottom = y + 40
    available_h = FRAME_H - header_bottom - 90
    available_w = FRAME_W - margin * 2

    scale = min(available_w / sw, available_h / sh)
    new_w = int(sw * scale)
    new_h = int(sh * scale)
    scaled = src.resize((new_w, new_h), Image.LANCZOS)

    # Round the corners of the screenshot
    radius = int(new_w * 0.045)
    mask = rounded_mask((new_w, new_h), radius)
    scaled.putalpha(mask)

    # Drop shadow
    shadow = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sx = (FRAME_W - new_w) // 2
    sy = header_bottom
    sd.rounded_rectangle([sx + 18, sy + 26, sx + new_w + 18, sy + new_h + 26],
                         radius=radius, fill=(0, 0, 0, 160))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=26))
    bg = Image.alpha_composite(bg, shadow)

    bg.paste(scaled, (sx, sy), scaled)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    bg.convert("RGB").save(out_path, format="PNG", optimize=True)
    print(f"wrote {out_path.relative_to(REPO_ROOT)}  ({FRAME_W}x{FRAME_H})")


SHOTS = [
    {
        "src": SCREENS / "device" / "home2.png",
        "headline": "Your pocket, organized",
        "subline": "Smart capture for screenshots, photos, PDFs, and shared text.",
        "out": "01_home.png",
    },
    {
        "src": SCREENS / "automation" / "pocket_settings2.png",
        "headline": "Pick your local LLM",
        "subline": "Qwen 3, Qwen 2.5, or Gemma 4 — downloaded once, runs offline.",
        "out": "02_models.png",
    },
    {
        "src": SCREENS / "automation" / "assistant_with_hello.png",
        "headline": "Ask anything",
        "subline": "A RAG assistant that knows what you've saved.",
        "out": "03_assistant.png",
    },
    {
        "src": SCREENS / "automation" / "chat_after_reply.png",
        "headline": "Runs on your device",
        "subline": "No cloud backend. No account. No telemetry.",
        "out": "04_local_ai.png",
    },
    {
        "src": SCREENS / "device" / "detail3.png",
        "headline": "Tasks and reminders",
        "subline": "Extracted from your items, scheduled locally on your phone.",
        "out": "05_tasks.png",
    },
    {
        "src": SCREENS / "device" / "scrolled.png",
        "headline": "Bills, messages, notes",
        "subline": "Auto-classified with dates, amounts, and contacts pulled out.",
        "out": "06_classified.png",
    },
]


if __name__ == "__main__":
    for shot in SHOTS:
        frame(shot["src"], shot["headline"], shot["subline"], OUT_DIR / shot["out"])
