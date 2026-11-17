"""Generates the Dimensional Rule Isolation icon (512x512, abstract)."""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

S = 512
rng = np.random.default_rng(7)

# --- background: deep navy gradient + soft radial glow + light noise -------------
top = np.array([0x0A, 0x0E, 0x1A], dtype=np.float32)
bot = np.array([0x14, 0x1B, 0x30], dtype=np.float32)
g = np.linspace(0.0, 1.0, S, dtype=np.float32)[:, None, None]
bg = top[None, None, :] * (1 - g) + bot[None, None, :] * g
bg = np.repeat(bg, S, axis=1)

yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
d = np.sqrt((xx - S * 0.5) ** 2 + (yy - S * 0.46) ** 2) / (S * 0.62)
glow = np.clip(1.0 - d, 0, 1) ** 2.2
bg += glow[..., None] * np.array([0x18, 0x2A, 0x4A], dtype=np.float32)
bg += rng.normal(0, 2.2, bg.shape).astype(np.float32)
base = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")

COLORS = [
    (0x2D, 0xD4, 0xBF),  # teal
    (0x9F, 0x7B, 0xFF),  # violet
    (0xFF, 0xB7, 0x3B),  # amber
    (0x6C, 0xC7, 0xFF),  # ice blue
]

CARD_W, CARD_H = 84, 300
GAP = 16
TOTAL = len(COLORS) * CARD_W + (len(COLORS) - 1) * GAP
START_X = (S - TOTAL) // 2
TOP_Y = 108


def make_card(color):
    img = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 1, CARD_W - 2, CARD_H - 2], radius=12,
                        fill=color + (120,), outline=color + (255,), width=4)
    for y in range(26, CARD_H - 14, 26):
        d.line([(10, y), (CARD_W - 10, y)], fill=color + (70,), width=1)
    for x in range(26, CARD_W - 10, 26):
        d.line([(x, 10), (x, CARD_H - 10)], fill=color + (70,), width=1)
    for i, width in enumerate((40, 54, 32)):
        y = 26 + i * 16
        d.rounded_rectangle([14, y, 14 + width, y + 7], radius=3, fill=(255, 255, 255, 235))
    return img


# --- glow layer behind the cards -------------------------------------------------
glow_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow_layer)
for i, color in enumerate(COLORS):
    x = START_X + i * (CARD_W + GAP)
    gd.rounded_rectangle([x - 8, TOP_Y - 8, x + CARD_W + 8, TOP_Y + CARD_H + 8],
                         radius=20, fill=color + (60,), outline=color + (190,), width=12)
glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(18))
base = Image.alpha_composite(base, glow_layer)

# --- cards ----------------------------------------------------------------------
for i, color in enumerate(COLORS):
    x = START_X + i * (CARD_W + GAP)
    base.alpha_composite(make_card(color), (x, TOP_Y))

# --- isolation hairlines between the cards --------------------------------------
sep = Image.new("RGBA", (S, S), (0, 0, 0, 0))
sd = ImageDraw.Draw(sep)
for i in range(len(COLORS) - 1):
    x = START_X + (i + 1) * CARD_W + i * GAP + GAP // 2
    sd.line([(x, TOP_Y - 14), (x, TOP_Y + CARD_H + 14)], fill=(235, 245, 255, 70), width=2)
base = Image.alpha_composite(base, sep)

# --- base shadow under the row ---------------------------------------------------
shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
sh = ImageDraw.Draw(shadow)
sh.ellipse([START_X - 30, TOP_Y + CARD_H + 6, START_X + TOTAL + 30, TOP_Y + CARD_H + 44],
           fill=(0, 0, 0, 120))
shadow = shadow.filter(ImageFilter.GaussianBlur(14))
base = Image.alpha_composite(base, shadow)

# --- vignette -------------------------------------------------------------------
vig = np.ones((S, S), dtype=np.float32)
dv = np.sqrt((xx - S / 2) ** 2 + (yy - S / 2) ** 2) / (S * 0.72)
vig *= np.clip(1.12 - 0.55 * dv ** 1.6, 0.55, 1.0)
arr = np.asarray(base.convert("RGB"), dtype=np.float32) * vig[..., None]
out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")

out.save(r"E:\git\dimension-rule-isolation\icon.png", "PNG", optimize=True)
out.resize((128, 128), Image.LANCZOS).save(
    r"E:\git\dimension-rule-isolation\icon_128.png", "PNG", optimize=True)
print("written icon.png", out.size)