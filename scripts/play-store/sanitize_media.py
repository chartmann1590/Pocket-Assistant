"""
Sanitize screenshot and demo-media assets by masking personal data.

Usage:
    python scripts/play-store/sanitize_media.py

This script overwrites files in place with deterministic redaction regions.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

REPO_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Rect:
    # Relative rectangle in 0..1 coordinates.
    x1: float
    y1: float
    x2: float
    y2: float
    mode: str = "blur"  # "blur" or "solid"


COMMON_TOP_BAR = Rect(0.0, 0.0, 1.0, 0.07, "solid")
COMMON_AD_STRIP = Rect(0.0, 0.93, 1.0, 1.0, "solid")
COMMON_CHAT_AVATAR = Rect(0.34, 0.008, 0.52, 0.055, "solid")

TARGETS: dict[str, list[Rect]] = {
    # Core screenshot sources used by docs + play-store captioned script.
    "screenshots/screen_now.png": [
        COMMON_TOP_BAR,
        COMMON_CHAT_AVATAR,
        Rect(0.06, 0.215, 0.90, 0.288, "solid"),  # reminder line with name
        Rect(0.09, 0.338, 0.83, 0.385, "solid"),  # proposed reminder title
    ],
    "screenshots/screenshot_assistant.png": [
        COMMON_TOP_BAR,
        COMMON_CHAT_AVATAR,
    ],
    "screenshots/screenshot_warning.png": [
        COMMON_TOP_BAR,
    ],
    "screenshots/screen_wrong.png": [
        COMMON_TOP_BAR,
        COMMON_CHAT_AVATAR,
    ],
    "screenshots/screen_wrong2.png": [
        COMMON_TOP_BAR,
        COMMON_CHAT_AVATAR,
        Rect(0.05, 0.248, 0.90, 0.347, "solid"),  # named entity in answer
    ],
    "screenshots/device/home2.png": [
        COMMON_TOP_BAR,
        Rect(0.05, 0.49, 0.95, 0.90, "blur"),  # recent items contain personal details
    ],
    "screenshots/device/screen.png": [
        COMMON_TOP_BAR,
        Rect(0.05, 0.49, 0.95, 0.90, "blur"),
    ],
    "docs/images/home.png": [
        COMMON_TOP_BAR,
        Rect(0.05, 0.49, 0.95, 0.90, "blur"),
    ],
    # Demo-video stills (source frames for foreground_service_demo.mp4).
    "play-store/demo-video/01_home.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/02_models.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/02_setup_scroll.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/03_download_btn.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/04_downloading.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/05_app_progress.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/05_notification.png": [
        COMMON_TOP_BAR,
        Rect(0.03, 0.08, 0.97, 0.95, "blur"),  # redact personal notifications
    ],
    "play-store/demo-video/05_progress2.png": [
        COMMON_TOP_BAR,
        Rect(0.02, 0.08, 0.45, 0.25, "solid"),  # date/weather widget
        Rect(0.02, 0.32, 0.98, 0.98, "blur"),  # app icons + folders
    ],
    "play-store/demo-video/05b_notif_scroll.png": [
        COMMON_TOP_BAR,
        Rect(0.03, 0.09, 0.97, 0.95, "blur"),  # redact personal notifications
    ],
    "play-store/demo-video/06_complete.png": [
        COMMON_TOP_BAR,
        COMMON_AD_STRIP,
    ],
    "play-store/demo-video/check.png": [
        COMMON_TOP_BAR,
        Rect(0.02, 0.08, 0.45, 0.25, "solid"),
        Rect(0.02, 0.32, 0.98, 0.98, "blur"),
    ],
}


def as_px(rect: Rect, width: int, height: int) -> tuple[int, int, int, int]:
    return (
        int(width * rect.x1),
        int(height * rect.y1),
        int(width * rect.x2),
        int(height * rect.y2),
    )


def redact_region(img: Image.Image, rect: Rect) -> Image.Image:
    w, h = img.size
    x1, y1, x2, y2 = as_px(rect, w, h)
    if x2 <= x1 or y2 <= y1:
        return img

    if rect.mode == "solid":
        overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay)
        draw.rounded_rectangle(
            [x1, y1, x2, y2],
            radius=14,
            fill=(10, 15, 30, 255),
        )
        return Image.alpha_composite(img.convert("RGBA"), overlay)

    crop = img.crop((x1, y1, x2, y2))
    blur = crop.filter(ImageFilter.GaussianBlur(radius=18))
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    overlay.paste(blur.convert("RGBA"), (x1, y1))
    draw = ImageDraw.Draw(overlay)
    draw.rounded_rectangle([x1, y1, x2, y2], radius=12, outline=(20, 28, 44, 220), width=2)
    return Image.alpha_composite(img.convert("RGBA"), overlay)


def sanitize_file(rel_path: str, rects: list[Rect]) -> None:
    path = REPO_ROOT / rel_path
    if not path.exists():
        print(f"skip missing: {rel_path}")
        return

    img = Image.open(path).convert("RGBA")
    for rect in rects:
        img = redact_region(img, rect)
    img.convert("RGB").save(path, format="PNG", optimize=True)
    print(f"sanitized: {rel_path}")


def sync_docs_assets() -> None:
    mapping = {
        "screenshots/screen_now.png": "docs/assets/screenshots/screen_now.png",
        "screenshots/screenshot_assistant.png": "docs/assets/screenshots/screenshot_assistant.png",
        "screenshots/screenshot_warning.png": "docs/assets/screenshots/screenshot_warning.png",
    }
    for src_rel, dst_rel in mapping.items():
        src = REPO_ROOT / src_rel
        dst = REPO_ROOT / dst_rel
        if not src.exists():
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        Image.open(src).convert("RGB").save(dst, format="PNG", optimize=True)
        print(f"synced: {dst_rel}")


def main() -> None:
    for rel_path, rects in TARGETS.items():
        sanitize_file(rel_path, rects)
    sync_docs_assets()


if __name__ == "__main__":
    main()
