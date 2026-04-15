"""Frame device screenshots as tablet-size images for the Play Store.

Play Store tablet requirements:
- 7-inch:  min 320px per side, max aspect 2:1. Common: 1200x1920 portrait.
- 10-inch: min 320px per side, max aspect 2:1. Common: 1600x2560 portrait.

We pad each phone screenshot onto tablet-sized branded canvases with the
same headline style used for phone screenshots.

Output:
  play-store/metadata/android/en-US/images/sevenInchScreenshots/NN_*.png
  play-store/metadata/android/en-US/images/tenInchScreenshots/NN_*.png
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[2]
IMAGES_DIR = REPO_ROOT / "play-store" / "metadata" / "android" / "en-US" / "images"
SCREENS = REPO_ROOT / "screenshots"

INDIGO_DEEP = (26, 35, 126)
INDIGO_LIGHT = (92, 107, 192)
WHITE = (255, 255, 255)
MUTED = (210, 218, 240)

SIZES = {
    "sevenInchScreenshots": (1200, 1920),
    "tenInchScreenshots": (1600, 2560),
}

STATUS_BAR_CROP = 140

SHOTS = [
    (SCREENS / "device" / "home2.png", "01_home.png", "Your pocket, organized", "Smart capture for screenshots, photos, PDFs, and shared text."),
    (SCREENS / "automation" / "pocket_settings2.png", "02_models.png", "Pick your local LLM", "Qwen 3, Qwen 2.5, or Gemma 4 — downloaded once, runs offline."),
    (SCREENS / "automation" / "assistant_with_hello.png", "03_assistant.png", "Ask anything", "A RAG assistant that knows what you've saved."),
    (SCREENS / "automation" / "chat_after_reply.png", "04_local_ai.png", "Runs on your device", "No cloud backend. No account. No telemetry."),
    (SCREENS / "device" / "detail3.png", "05_tasks.png", "Tasks and reminders", "Extracted from your items, scheduled locally on your phone."),
    (SCREENS / "device" / "scrolled.png", "06_classified.png", "Bills, messages, notes", "Auto-classified with dates, amounts, and contacts pulled out."),
]


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


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255
    )
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


def frame_tablet(
    raw_src: Path, headline: str, subline: str,
    canvas_size: tuple[int, int], out_path: Path
) -> None:
    cw, ch = canvas_size
    scale_factor = cw / 1080

    bg = gradient_bg(canvas_size).convert("RGBA")

    # Diagonal highlight
    overlay = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon(
        [(0, ch), (cw, 0), (cw, int(ch * 0.18)), (0, int(ch * 1.15))],
        fill=(255, 255, 255, 16),
    )
    bg = Image.alpha_composite(bg, overlay)
    draw = ImageDraw.Draw(bg)

    # Headline + subline
    title_size = int(74 * scale_factor)
    sub_size = int(36 * scale_factor)
    margin = int(72 * scale_factor)
    text_top = int(96 * scale_factor)
    max_text_w = cw - margin * 2

    title_font = load_font(title_size, bold=True)
    sub_font = load_font(sub_size)

    title_lines = wrap_text(headline, title_font, max_text_w, draw)
    y = text_top
    for line in title_lines:
        draw.text((margin + 3, y + 3), line, font=title_font, fill=(0, 0, 0, 110))
        draw.text((margin, y), line, font=title_font, fill=WHITE)
        y += title_font.size + 8

    y += 4
    for sub in wrap_text(subline, sub_font, max_text_w, draw):
        draw.text((margin, y), sub, font=sub_font, fill=MUTED)
        y += sub_font.size + 4

    # Load raw device screenshot and crop status bar
    src = Image.open(raw_src).convert("RGBA")
    sw, sh = src.size
    src = src.crop((0, STATUS_BAR_CROP, sw, sh))
    sw, sh = src.size

    # Scale to fit under the header
    header_bottom = y + int(40 * scale_factor)
    available_h = ch - header_bottom - int(90 * scale_factor)
    available_w = cw - margin * 2

    scale = min(available_w / sw, available_h / sh)
    new_w = int(sw * scale)
    new_h = int(sh * scale)
    scaled = src.resize((new_w, new_h), Image.LANCZOS)

    # Round corners
    radius = int(new_w * 0.035)
    mask = rounded_mask((new_w, new_h), radius)
    scaled.putalpha(mask)

    # Drop shadow
    shadow = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    sx = (cw - new_w) // 2
    sy = header_bottom
    sd = ImageDraw.Draw(shadow)
    shadow_off = int(20 * scale_factor)
    sd.rounded_rectangle(
        [sx + shadow_off, sy + shadow_off + 6,
         sx + new_w + shadow_off, sy + new_h + shadow_off + 6],
        radius=radius, fill=(0, 0, 0, 160),
    )
    blur_r = int(26 * scale_factor)
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=blur_r))
    bg = Image.alpha_composite(bg, shadow)

    bg.paste(scaled, (sx, sy), scaled)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    bg.convert("RGB").save(out_path, format="PNG", optimize=True)
    print(f"wrote {out_path.relative_to(REPO_ROOT)}  ({cw}x{ch})")


if __name__ == "__main__":
    for folder_name, canvas_size in SIZES.items():
        out_dir = IMAGES_DIR / folder_name
        for raw_path, out_name, headline, subline in SHOTS:
            if not raw_path.exists():
                print(f"SKIP {raw_path.name} (not found)")
                continue
            frame_tablet(raw_path, headline, subline, canvas_size, out_dir / out_name)
