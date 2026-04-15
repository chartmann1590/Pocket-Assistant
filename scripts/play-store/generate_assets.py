"""
Build captioned phone screenshots for Google Play under ../../play-store/captioned/.

Requires: Pillow (`py -m pip install pillow`).
Run from repo root: py scripts/play-store/generate_assets.py
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = REPO_ROOT / "play-store" / "captioned"
LISTING_SRC = Path(__file__).resolve().parent / "store-listing.txt"
LISTING_DST = REPO_ROOT / "play-store" / "store-listing.txt"

# Material-style primary from app theme (light)
BAR_COLOR = (51, 102, 255)
TEXT_COLOR = (255, 255, 255)
SUBTITLE_COLOR = (220, 228, 255)

# Target width; height follows screenshot + caption bar
TARGET_WIDTH = 1080
CAPTION_MIN_HEIGHT = 168
PADDING_X = 48
PADDING_Y = 36


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        Path(r"C:\Windows\Fonts\segoeuib.ttf"),
        Path(r"C:\Windows\Fonts\segoeui.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
        Path("/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
    ]
    for p in candidates:
        if p.is_file():
            try:
                return ImageFont.truetype(str(p), size)
            except OSError:
                continue
    return ImageFont.load_default()


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> list[str]:
    words = text.split()
    if not words:
        return []
    lines: list[str] = []
    current: list[str] = []
    for w in words:
        trial = " ".join(current + [w])
        bbox = draw.textbbox((0, 0), trial, font=font)
        if bbox[2] - bbox[0] <= max_width:
            current.append(w)
        else:
            if current:
                lines.append(" ".join(current))
            current = [w]
    if current:
        lines.append(" ".join(current))
    return lines


def compose(
    src_rel: str,
    out_name: str,
    title: str,
    subtitle: str,
    crop_rel: tuple[float, float, float, float] | None = None,
) -> None:
    """
    crop_rel: optional (left, top, right, bottom) each in 0..1 relative to
    source size, applied before scaling (e.g. focus on chat or a card).
    """
    src = REPO_ROOT / src_rel
    if not src.is_file():
        print(f"Skip missing source: {src}", file=sys.stderr)
        return

    img = Image.open(src).convert("RGB")
    w, h = img.size
    if crop_rel is not None:
        l, t, r, b = crop_rel
        box = (
            max(0, int(w * l)),
            max(0, int(h * t)),
            min(w, int(w * r)),
            min(h, int(h * b)),
        )
        if box[2] <= box[0] or box[3] <= box[1]:
            print(f"Skip bad crop for {src_rel}", file=sys.stderr)
            return
        img = img.crop(box)
        w, h = img.size
    scale = TARGET_WIDTH / w
    new_h = int(h * scale)
    img = img.resize((TARGET_WIDTH, new_h), Image.Resampling.LANCZOS)

    title_font = load_font(42)
    sub_font = load_font(26)
    dummy = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    max_text_w = TARGET_WIDTH - 2 * PADDING_X
    title_lines = wrap_text(dummy, title, title_font, max_text_w)
    sub_lines = wrap_text(dummy, subtitle, sub_font, max_text_w)

    line_h_title = title_font.getbbox("Ay")[3] - title_font.getbbox("Ay")[1]
    line_h_sub = sub_font.getbbox("Ay")[3] - sub_font.getbbox("Ay")[1]
    gap = 12
    caption_h = max(
        CAPTION_MIN_HEIGHT,
        PADDING_Y * 2
        + len(title_lines) * (line_h_title + 6)
        + len(sub_lines) * (line_h_sub + gap)
        + 8,
    )

    out = Image.new("RGB", (TARGET_WIDTH, new_h + caption_h), BAR_COLOR)
    out.paste(img, (0, 0))
    draw = ImageDraw.Draw(out)

    y = new_h + PADDING_Y
    for line in title_lines:
        draw.text((PADDING_X, y), line, font=title_font, fill=TEXT_COLOR)
        y += line_h_title + 6
    y += gap
    for line in sub_lines:
        draw.text((PADDING_X, y), line, font=sub_font, fill=SUBTITLE_COLOR)
        y += line_h_sub + gap

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = OUT_DIR / out_name
    out.save(out_path, "PNG", optimize=True)
    print(f"Wrote {out_path.relative_to(REPO_ROOT)}")


def main() -> None:
    REPO_ROOT.joinpath("play-store").mkdir(parents=True, exist_ok=True)
    shutil.copy2(LISTING_SRC, LISTING_DST)
    print(f"Copied listing to {LISTING_DST.relative_to(REPO_ROOT)}")

    # (source_rel, output, title, subtitle) or + (crop_rel) for focused frame
    slides: list[
        tuple[str, str, str, str] | tuple[str, str, str, str, tuple[float, float, float, float]]
    ] = [
        (
            "screenshots/device/home2.png",
            "00-main-home-screen.png",
            "Main screen at a glance",
            "Quick actions plus recent items are all in one place on your home view.",
        ),
        (
            "play-store/demo-video/02_models.png",
            "07-model-picker-overview.png",
            "See all local model options",
            "Compare lightweight and larger models before downloading on-device AI.",
        ),
        (
            "screenshots/screen_now.png",
            "01-reminders-from-chat.png",
            "Turn chat into reminders",
            "Ask in plain language—confirm a proposed reminder and it’s on your calendar.",
        ),
        (
            "screenshots/screenshot_assistant.png",
            "02-assistant-and-saved-items.png",
            "Chat with your saved stuff",
            "Jump from answers to the original bill or note when you need the full picture.",
        ),
        (
            "screenshots/screen_wrong.png",
            "03-instant-answers.png",
            "Instant answers on due dates",
            "Ask about trips, bills, or anything tied to what you’ve saved—then open the source in one tap.",
        ),
        (
            "screenshots/screen_now.png",
            "04-review-before-save.png",
            "Review before it’s saved",
            "See proposed reminder details and confirm or dismiss without leaving the thread.",
            (0.0, 0.14, 1.0, 0.82),
        ),
        (
            "screenshots/screenshot_assistant.png",
            "05-ask-in-your-words.png",
            "Ask in your own words",
            "No rigid commands—type naturally and follow up with quick replies when they help.",
            (0.0, 0.0, 1.0, 0.62),
        ),
        (
            "screenshots/screen_wrong.png",
            "06-chat-history-context.png",
            "Context from your imports",
            "The assistant stays grounded in items you imported so answers match your real documents.",
            (0.0, 0.08, 1.0, 0.58),
        ),
    ]

    for row in slides:
        if len(row) == 4:
            src, out, title, sub = row
            compose(src, out, title, sub)
        else:
            src, out, title, sub, crop = row
            compose(src, out, title, sub, crop_rel=crop)

    print(
        "Done. Upload PNGs from play-store/captioned/ in Play Console "
        "(Main store listing > Phone screenshots)."
    )


if __name__ == "__main__":
    main()
