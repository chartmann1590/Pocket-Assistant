"""Build a demo video for FOREGROUND_SERVICE_DATA_SYNC permission."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import subprocess

DIR = Path(__file__).parent
OUT = DIR / "frames"
OUT.mkdir(exist_ok=True)

# Caption bar colors
BAR_BG = (20, 20, 50, 220)
TEXT_COLOR = (255, 255, 255)
ACCENT = (130, 180, 255)

def load_font(size, bold=False):
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

TITLE_FONT = load_font(38, bold=True)
BODY_FONT = load_font(28)
SMALL_FONT = load_font(22)

def add_caption(img: Image.Image, title: str, body: str, step: str) -> Image.Image:
    """Add a caption bar at the bottom of the image."""
    w, h = img.size
    bar_h = 220
    canvas = Image.new("RGBA", (w, h + bar_h), (0, 0, 0, 255))
    canvas.paste(img.convert("RGBA"), (0, 0))

    # Draw caption background
    draw = ImageDraw.Draw(canvas)
    draw.rectangle([0, h, w, h + bar_h], fill=(15, 15, 40))

    # Step indicator
    draw.text((30, h + 15), step, font=SMALL_FONT, fill=ACCENT)

    # Title
    draw.text((30, h + 50), title, font=TITLE_FONT, fill=TEXT_COLOR)

    # Body
    y = h + 100
    words = body.split()
    line = ""
    for word in words:
        test = (line + " " + word).strip()
        if draw.textlength(test, font=BODY_FONT) > w - 60:
            draw.text((30, y), line, font=BODY_FONT, fill=(200, 210, 230))
            y += 36
            line = word
        else:
            line = test
    if line:
        draw.text((30, y), line, font=BODY_FONT, fill=(200, 210, 230))

    return canvas.convert("RGB")


# Define the frames sequence
FRAMES = [
    {
        "src": "01_home.png",
        "step": "STEP 1 OF 5",
        "title": "User opens Pocket Assistant",
        "body": "The setup wizard lets the user choose an AI processing mode. \"Local only\" keeps all data on-device.",
        "duration": 5,
    },
    {
        "src": "02_setup_scroll.png",
        "step": "STEP 2 OF 5",
        "title": "User selects an AI model to download",
        "body": "Multiple local LLM models are available. Qwen3 0.6B (586 MB) is the default lightweight option.",
        "duration": 5,
    },
    {
        "src": "03_download_btn.png",
        "step": "STEP 3 OF 5",
        "title": "User taps \"Download lightweight model\"",
        "body": "This triggers ModelDownloadWorker — a WorkManager CoroutineWorker that calls setForeground() with FOREGROUND_SERVICE_TYPE_DATA_SYNC.",
        "duration": 6,
    },
    {
        "src": "04_downloading.png",
        "step": "STEP 4 OF 5",
        "title": "Foreground service syncs model data",
        "body": "The worker downloads the LiteRT-LM model file from Hugging Face. A persistent notification with progress (8% / 50 MB of 585 MB) is shown via ForegroundInfo.",
        "duration": 7,
    },
    {
        "src": "06_complete.png",
        "step": "STEP 5 OF 5",
        "title": "Download complete — model ready",
        "body": "The foreground service ends, notification is dismissed. The model is now installed locally for offline AI inference.",
        "duration": 5,
    },
]

# Also add an intro and summary frame
def make_text_frame(w, h, lines, bg=(15, 15, 40)):
    img = Image.new("RGB", (w, h), bg)
    draw = ImageDraw.Draw(img)
    total_h = len(lines) * 60
    y = (h - total_h) // 2
    for text, font, color in lines:
        tw = draw.textlength(text, font=font)
        draw.text(((w - tw) / 2, y), text, font=font, fill=color)
        y += 60
    return img

# Get dimensions from first image
first = Image.open(DIR / "01_home.png")
W, H_img = first.size
H_total = H_img + 220  # with caption bar

# Intro frame
intro = make_text_frame(W, H_total, [
    ("Pocket Assistant", load_font(48, bold=True), (255, 255, 255)),
    ("", BODY_FONT, TEXT_COLOR),
    ("FOREGROUND_SERVICE_DATA_SYNC", load_font(30, bold=True), ACCENT),
    ("Permission Demonstration", BODY_FONT, (200, 210, 230)),
    ("", BODY_FONT, TEXT_COLOR),
    ("Downloads AI model files from", BODY_FONT, (180, 190, 210)),
    ("Hugging Face for on-device inference", BODY_FONT, (180, 190, 210)),
])
intro.save(OUT / "frame_000.png")

# Summary frame
summary = make_text_frame(W, H_total, [
    ("Summary", load_font(48, bold=True), (255, 255, 255)),
    ("", BODY_FONT, TEXT_COLOR),
    ("FOREGROUND_SERVICE_DATA_SYNC is used to:", load_font(26, bold=True), ACCENT),
    ("", SMALL_FONT, TEXT_COLOR),
    ("Download large AI model files (586MB-3.5GB)", BODY_FONT, (200, 210, 230)),
    ("from Hugging Face via ModelDownloadWorker", BODY_FONT, (200, 210, 230)),
    ("", SMALL_FONT, TEXT_COLOR),
    ("User-initiated \u2022 Shows progress notification", BODY_FONT, (180, 190, 210)),
    ("Foreground service ensures reliable download", BODY_FONT, (180, 190, 210)),
])
summary.save(OUT / "frame_999.png")

# Process each frame
for i, frame in enumerate(FRAMES, 1):
    src = Image.open(DIR / frame["src"])
    annotated = add_caption(src, frame["title"], frame["body"], frame["step"])
    # Resize to match consistent dimensions
    annotated = annotated.resize((W, H_total), Image.LANCZOS)
    annotated.save(OUT / f"frame_{i:03d}.png")
    print(f"  frame_{i:03d}.png <- {frame['src']}")

# Build ffmpeg concat file
durations = [4] + [f["duration"] for f in FRAMES] + [5]
frame_files = sorted(OUT.glob("frame_*.png"))

concat = DIR / "concat.txt"
with open(concat, "w") as f:
    for fpath, dur in zip(frame_files, durations):
        # ffmpeg concat demuxer needs forward slashes
        p = str(fpath).replace("\\", "/")
        f.write(f"file '{p}'\n")
        f.write(f"duration {dur}\n")
    # Repeat last frame (ffmpeg concat quirk)
    p = str(frame_files[-1]).replace("\\", "/")
    f.write(f"file '{p}'\n")

print(f"\nWrote {len(frame_files)} frames + concat.txt")
print("Run: ffmpeg -f concat -safe 0 -i concat.txt -vf \"scale=1008:2464\" -c:v libx264 -pix_fmt yuv420p -r 1 demo.mp4")
