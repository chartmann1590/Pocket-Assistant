"""Render Play Store artwork from scratch using Pillow.

Outputs (paths relative to repo root):
- play-store/metadata/android/en-US/images/icon/icon.png              512x512
- play-store/metadata/android/en-US/images/featureGraphic/featureGraphic.png  1024x500

Design reference: app/src/main/res/drawable/ic_launcher_*.xml — deep indigo
background with a rounded pocket, an AI lightbulb, and small sparkles.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[2]
IMAGES_DIR = REPO_ROOT / "play-store" / "metadata" / "android" / "en-US" / "images"

INDIGO_DEEP = (26, 35, 126)       # #1A237E
INDIGO_MID = (40, 53, 147)        # #283593
INDIGO_LIGHT = (92, 107, 192)     # #5C6BC0
WHITE = (255, 255, 255)
OFF_WHITE = (245, 247, 252)

# ---------- helpers ----------------------------------------------------------

def radial_bg(size: tuple[int, int], center: tuple[float, float],
              inner: tuple[int, int, int], outer: tuple[int, int, int]) -> Image.Image:
    """Cheap radial gradient from `inner` at `center` fading to `outer` at corners."""
    w, h = size
    img = Image.new("RGB", size, outer)
    px = img.load()
    cx, cy = center
    max_dist = math.hypot(max(cx, w - cx), max(cy, h - cy))
    for y in range(h):
        for x in range(w):
            t = min(1.0, math.hypot(x - cx, y - cy) / max_dist)
            px[x, y] = (
                int(inner[0] * (1 - t) + outer[0] * t),
                int(inner[1] * (1 - t) + outer[1] * t),
                int(inner[2] * (1 - t) + outer[2] * t),
            )
    return img


def sparkle(draw: ImageDraw.ImageDraw, cx: float, cy: float, size: float,
            color: tuple[int, int, int, int] = (255, 255, 255, 255)) -> None:
    """Four-point diamond sparkle."""
    pts = [
        (cx, cy - size),
        (cx + size * 0.35, cy - size * 0.35),
        (cx + size, cy),
        (cx + size * 0.35, cy + size * 0.35),
        (cx, cy + size),
        (cx - size * 0.35, cy + size * 0.35),
        (cx - size, cy),
        (cx - size * 0.35, cy - size * 0.35),
    ]
    draw.polygon(pts, fill=color)


def pocket_with_bulb(canvas_size: int, scale: float = 1.0,
                     center: tuple[float, float] | None = None,
                     glow: bool = True) -> Image.Image:
    """Return an RGBA layer containing the pocket+bulb mark centered in a
    transparent canvas of canvas_size x canvas_size.
    `scale` sizes the mark relative to canvas_size (1.0 ≈ full frame).
    """
    img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if center is None:
        cx = cy = canvas_size / 2
    else:
        cx, cy = center

    # Pocket body (rounded rect)
    base = canvas_size * 0.44 * scale
    pw = base
    ph = base * 1.05
    px0 = cx - pw / 2
    py0 = cy - ph / 2 + canvas_size * 0.02 * scale
    px1 = cx + pw / 2
    py1 = cy + ph / 2 + canvas_size * 0.02 * scale
    radius = pw * 0.18
    draw.rounded_rectangle([px0, py0, px1, py1], radius=radius, fill=WHITE)

    # Pocket flap (darker curved top)
    flap_h = ph * 0.22
    draw.rounded_rectangle(
        [px0, py0, px1, py0 + flap_h],
        radius=radius,
        fill=INDIGO_DEEP,
    )
    # Bottom edge of flap to feel like a curve with an inset
    draw.rectangle([px0, py0 + flap_h - 2, px1, py0 + flap_h + 2], fill=INDIGO_DEEP)

    # Inner inset (soft translucent)
    inset = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    inset_draw = ImageDraw.Draw(inset)
    inset_draw.rounded_rectangle(
        [px0 + pw * 0.08, py0 + flap_h + ph * 0.02, px1 - pw * 0.08, py1 - ph * 0.05],
        radius=radius * 0.7,
        fill=(26, 35, 126, 38),
    )
    img = Image.alpha_composite(img, inset)
    draw = ImageDraw.Draw(img)

    # Lightbulb body
    bulb_cx = cx
    bulb_cy = cy + ph * 0.05
    bulb_r = pw * 0.18
    if glow:
        glow_layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow_layer)
        gr = bulb_r * 2.6
        gd.ellipse([bulb_cx - gr, bulb_cy - gr, bulb_cx + gr, bulb_cy + gr],
                   fill=(255, 235, 160, 120))
        glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=canvas_size * 0.03))
        img = Image.alpha_composite(img, glow_layer)
        draw = ImageDraw.Draw(img)

    draw.ellipse(
        [bulb_cx - bulb_r, bulb_cy - bulb_r, bulb_cx + bulb_r, bulb_cy + bulb_r],
        fill=WHITE,
    )
    # Bulb base stem
    stem_w = bulb_r * 0.7
    stem_h = bulb_r * 0.55
    draw.rounded_rectangle(
        [bulb_cx - stem_w / 2, bulb_cy + bulb_r * 0.75,
         bulb_cx + stem_w / 2, bulb_cy + bulb_r * 0.75 + stem_h],
        radius=stem_w * 0.25,
        fill=WHITE,
    )
    # Base rings (dark indigo)
    for i in range(3):
        ry = bulb_cy + bulb_r * 0.78 + i * (stem_h / 3.2)
        draw.rectangle(
            [bulb_cx - stem_w / 2 + 2, ry,
             bulb_cx + stem_w / 2 - 2, ry + max(2, stem_h / 10)],
            fill=INDIGO_DEEP,
        )
    # AI circuit inside bulb
    lw = max(2, int(canvas_size * 0.006))
    draw.line([(bulb_cx, bulb_cy - bulb_r * 0.4), (bulb_cx, bulb_cy)],
              fill=INDIGO_DEEP, width=lw)
    draw.line([(bulb_cx, bulb_cy), (bulb_cx - bulb_r * 0.5, bulb_cy + bulb_r * 0.35)],
              fill=INDIGO_DEEP, width=lw)
    draw.line([(bulb_cx, bulb_cy), (bulb_cx + bulb_r * 0.5, bulb_cy + bulb_r * 0.35)],
              fill=INDIGO_DEEP, width=lw)
    node_r = max(2, int(canvas_size * 0.008))
    for nx, ny in [
        (bulb_cx, bulb_cy - bulb_r * 0.4),
        (bulb_cx - bulb_r * 0.5, bulb_cy + bulb_r * 0.35),
        (bulb_cx + bulb_r * 0.5, bulb_cy + bulb_r * 0.35),
    ]:
        draw.ellipse([nx - node_r, ny - node_r, nx + node_r, ny + node_r],
                     fill=INDIGO_DEEP)

    # Sparkles around the pocket
    sparkle(draw, px1 + pw * 0.05, py0 + ph * 0.15, canvas_size * 0.035 * scale)
    sparkle(draw, px0 - pw * 0.03, py0 + ph * 0.18, canvas_size * 0.028 * scale)
    sparkle(draw, px1 + pw * 0.08, py1 - ph * 0.1,
            canvas_size * 0.02 * scale, color=(255, 255, 255, 180))

    return img


# ---------- icon -------------------------------------------------------------

def render_icon(out_path: Path, size: int = 512) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    bg = radial_bg((size, size), (size * 0.55, size * 0.45), INDIGO_LIGHT, INDIGO_DEEP)
    # Add a subtle diagonal highlight
    hi = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hi_draw = ImageDraw.Draw(hi)
    hi_draw.polygon([(0, size), (size, 0), (size, size * 0.2), (0, size * 1.2)],
                    fill=(255, 255, 255, 20))
    bg = Image.alpha_composite(bg.convert("RGBA"), hi)
    mark = pocket_with_bulb(size, scale=1.0)
    final = Image.alpha_composite(bg, mark)
    # Play Store icon must NOT have an alpha channel
    final.convert("RGB").save(out_path, format="PNG", optimize=True)
    print(f"wrote {out_path.relative_to(REPO_ROOT)}  ({size}x{size})")


# ---------- feature graphic --------------------------------------------------

def load_font(size: int) -> ImageFont.FreeTypeFont:
    candidates = [
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/seguisb.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for c in candidates:
        try:
            return ImageFont.truetype(c, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def render_feature_graphic(out_path: Path, size: tuple[int, int] = (1024, 500)) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    w, h = size
    bg = radial_bg(size, (w * 0.8, h * 0.6), INDIGO_LIGHT, INDIGO_DEEP)

    overlay = Image.new("RGBA", size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)

    # Soft diagonal light
    od.polygon([(0, h), (w, 0), (w, h * 0.25), (0, h * 1.2)], fill=(255, 255, 255, 18))

    # Decorative star field (sparkles)
    import random
    rng = random.Random(17)
    for _ in range(38):
        sx = rng.uniform(0, w * 0.6)
        sy = rng.uniform(0, h)
        ss = rng.uniform(3, 9)
        sparkle(od, sx, sy, ss,
                color=(255, 255, 255, int(rng.uniform(40, 160))))

    bg = Image.alpha_composite(bg.convert("RGBA"), overlay)

    # Mark on the right side, vertically centered
    mark = pocket_with_bulb(h, scale=1.15)
    mark_x = int(w - h * 0.96)
    mark_y = 0
    bg.alpha_composite(mark, (mark_x, mark_y))

    draw = ImageDraw.Draw(bg)

    # Title
    title_font = load_font(86)
    sub_font = load_font(34)
    tag_font = load_font(26)

    title = "Pocket Assistant"
    subtitle = "Local-first AI organizer"
    pill = "OCR • On-device LLM • Tasks"

    # Title shadow + fill
    tx = 56
    ty = 120
    for dx, dy in [(3, 3), (2, 2)]:
        draw.text((tx + dx, ty + dy), title, font=title_font, fill=(0, 0, 0, 90))
    draw.text((tx, ty), title, font=title_font, fill=WHITE)

    # Subtitle
    draw.text((tx + 2, ty + 110), subtitle, font=sub_font, fill=(220, 225, 245))

    # Pill chip
    pill_pad_x, pill_pad_y = 22, 10
    bbox = draw.textbbox((0, 0), pill, font=tag_font)
    pw = bbox[2] - bbox[0] + pill_pad_x * 2
    ph = bbox[3] - bbox[1] + pill_pad_y * 2
    px = tx
    py = ty + 180
    draw.rounded_rectangle([px, py, px + pw, py + ph], radius=ph / 2,
                           fill=(255, 255, 255, 40), outline=(255, 255, 255, 120),
                           width=2)
    draw.text((px + pill_pad_x, py + pill_pad_y - 2), pill, font=tag_font,
              fill=WHITE)

    # Play Store feature graphic must NOT have an alpha channel
    bg.convert("RGB").save(out_path, format="PNG", optimize=True)
    print(f"wrote {out_path.relative_to(REPO_ROOT)}  ({w}x{h})")


if __name__ == "__main__":
    render_icon(IMAGES_DIR / "icon" / "icon.png")
    render_feature_graphic(IMAGES_DIR / "featureGraphic" / "featureGraphic.png")
