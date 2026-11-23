#!/usr/bin/env python3
"""Regenerate Android launcher assets from the AI-generated master. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
source = Image.open(root / "artwork/funnytool-icon.png").convert("RGB")
background = (16, 29, 45)

# Adaptive foreground occupies 66% of its 108dp canvas, retaining the full symbol
# inside the guaranteed 66dp safe circle even after the launcher applies its mask.
layer = Image.new("RGB", (432, 432), background)
layer.paste(source.resize((276, 276), Image.Resampling.LANCZOS), (78, 78))
(res / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
layer.save(res / "drawable-nodpi/ic_launcher_foreground.png", optimize=True)
source.resize((192, 192), Image.Resampling.LANCZOS).save(res / "drawable-nodpi/ic_brand.png", optimize=True)
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    dest = res / f"mipmap-{density}"
    dest.mkdir(parents=True, exist_ok=True)
    icon = source.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=size // 5, fill=255)
    icon.putalpha(mask)
    icon.save(dest / "ic_launcher.png", optimize=True)
    # Clear mask first: the round variant must not retain rounded-square corners.
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    icon.putalpha(mask)
    icon.save(dest / "ic_launcher_round.png", optimize=True)
print("Generated adaptive foreground, brand image and five launcher densities.")
