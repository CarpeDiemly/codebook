"""把 design/icon.svg 的设计用 Pillow 重绘成 PNG 预览（本机 Chrome 渲染被沙箱拦截）。

用法: python tools/render_icon_preview.py
输出: .tools/icon-preview.png（方形）、.tools/icon-preview-circle.png（圆形裁切）、
      docs/assets/app-icon.png（文档用圆形预览）
"""
import os
from PIL import Image, ImageDraw

S = 4  # 设计稿 108x108，导出 432x432
W = H = 108 * S
C0 = (0x0E, 0x21, 0x48)
C1 = (0x24, 0x5A, 0x9E)


def px(v: float) -> int:
    return int(round(v * S))


def gradient_background() -> Image.Image:
    img = Image.new("RGB", (W, H))
    pixels = img.load()
    denom = (W - 1) + (H - 1)
    for y in range(H):
        for x in range(W):
            t = (x + y) / denom
            pixels[x, y] = (
                int(C0[0] + (C1[0] - C0[0]) * t),
                int(C0[1] + (C1[1] - C0[1]) * t),
                int(C0[2] + (C1[2] - C0[2]) * t),
            )
    return img


def build() -> Image.Image:
    base = gradient_background().convert("RGBA")

    # 书本剪影（白色），中间钥匙孔挖空
    book = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(book)
    d.rounded_rectangle([px(33), px(27), px(75), px(81)], radius=px(5), fill=255)
    d.ellipse([px(54 - 5.0), px(49.6 - 5.0), px(54 + 5.0), px(49.6 + 5.0)], fill=0)
    d.polygon(
        [(px(51.6), px(53.4)), (px(56.4), px(53.4)), (px(57.3), px(66.0)), (px(50.7), px(66.0))],
        fill=0,
    )

    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    base.paste(white, (0, 0), book)

    # 书脊：深蓝薄纱（22% 透明度），只作用在书页范围内
    spine = Image.new("L", (W, H), 0)
    ds = ImageDraw.Draw(spine)
    ds.rounded_rectangle([px(33), px(27), px(39.5), px(81)], radius=px(3.25), fill=255)
    spine = Image.composite(spine, Image.new("L", (W, H), 0), book)
    spine_alpha = spine.point(lambda v: int(v * 0.22))
    navy = Image.new("RGBA", (W, H), (0x0E, 0x21, 0x48, 0))
    navy.putalpha(spine_alpha)
    base = Image.alpha_composite(base, navy)

    # 书签丝带
    ribbon = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(ribbon)
    dr.polygon(
        [
            (px(64.0), px(27)),
            (px(71.0), px(27)),
            (px(71.0), px(44.5)),
            (px(67.5), px(41.5)),
            (px(64.0), px(44.5)),
        ],
        fill=(0x3F, 0xD0, 0xD4, 255),
    )
    return Image.alpha_composite(base, ribbon)


def main() -> None:
    root = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
    out_dir = os.path.join(root, ".tools")
    docs_dir = os.path.join(root, "docs", "assets")
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(docs_dir, exist_ok=True)

    icon = build()
    square = os.path.join(out_dir, "icon-preview.png")
    icon.convert("RGB").save(square)

    circle_mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(circle_mask).ellipse([0, 0, W - 1, H - 1], fill=255)
    round_icon = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    round_icon.paste(icon, (0, 0), circle_mask)
    circle = os.path.join(out_dir, "icon-preview-circle.png")
    round_icon.convert("RGB").save(circle)

    doc_icon = os.path.join(docs_dir, "app-icon.png")
    round_icon.resize((216, 216), Image.LANCZOS).convert("RGB").save(doc_icon)
    print("saved", square, circle, doc_icon)


if __name__ == "__main__":
    main()
