#!/usr/bin/env python3
"""تجهيز أيقونة التطبيق بكل كثافات أندرويد"""
from PIL import Image
import os

SRC = "/workspace/generated_images/generated_image_92eb97cc-179d-479e-83b0-37e6edc11640_0.png"
BASE = "/workspace/ff-turbonet-app/app/res"

densities = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

img = Image.open(SRC).convert("RGBA")
for folder, size in densities.items():
    d = os.path.join(BASE, folder)
    os.makedirs(d, exist_ok=True)
    resized = img.resize((size, size), Image.LANCZOS)
    out = os.path.join(d, "ic_launcher.png")
    resized.save(out, "PNG")
    print(f"OK {out} ({size}x{size})")

print("ICONS-DONE")
