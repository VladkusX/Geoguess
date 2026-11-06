"""Ikony CnC: te same prymitywy dają SVG (podgląd) i Android vector (później)."""
import json, sys

INK = "#111111"; YEL = "#FFE14D"; BEIGE = "#E9C9A0"; BLACK = "#1B1B1B"; ORANGE = "#FF5A36"; MINT = "#3EF08A"
NOTE = {"5": "#B9B9B9", "10": "#F08A8A", "20": "#7FB2F0", "50": "#FFA552", "100": "#7FD49A"}

def P(d, fill=None, stroke=None, sw=0, cap=None):
    return {"t": "p", "d": d, "fill": fill, "stroke": stroke, "sw": sw, "cap": cap}

def G(children, tx=0, ty=0, s=1, rot=0, px=0, py=0):
    return {"t": "g", "c": children, "tx": tx, "ty": ty, "s": s, "rot": rot, "px": px, "py": py}

def circle(x, y, r):
    return f"M{x-r},{y} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0 Z"

def rrect(x, y, w, h, r):
    return (f"M{x+r},{y} L{x+w-r},{y} Q{x+w},{y} {x+w},{y+r} L{x+w},{y+h-r} Q{x+w},{y+h} {x+w-r},{y+h} "
            f"L{x+r},{y+h} Q{x},{y+h} {x},{y+h-r} L{x},{y+r} Q{x},{y} {x+r},{y} Z")

def euro(cx, cy, k=1.0, color=INK, sw=2.2):
    r = 6 * k
    return [
        P(f"M{cx+r*0.75},{cy-r*0.75} A{r},{r} 0 1,0 {cx+r*0.75},{cy+r*0.75}", stroke=color, sw=sw, cap="round"),
        P(f"M{cx-r*1.2},{cy-r*0.25} L{cx+r*0.3},{cy-r*0.25} M{cx-r*1.2},{cy+r*0.35} L{cx+r*0.3},{cy+r*0.35}",
          stroke=color, sw=sw*0.8, cap="round"),
    ]

def note(cx, cy, w=32, h=19, kind="50", rot=0):
    x, y = cx - w / 2, cy - h / 2
    body = [
        P(rrect(x, y, w, h, 2.5), fill=NOTE[kind], stroke=INK, sw=2),
        P(rrect(x + 3, y + 3, w - 6, h - 6, 1.5), stroke="#00000055", sw=1),
        P(circle(cx, cy, h * 0.33), fill="#FFFFFFAA"),
    ] + euro(cx, cy, k=h / 30, sw=1.8)
    return G(body, rot=rot, px=cx, py=cy)

HEAD = "M30,50 L35,26 L49,40 Q54,38 59,40 L73,26 L78,50 Q84,78 54,84 Q24,78 30,50 Z"

def cat(cx, cy, s, black=False, stroke=INK):
    fill = BLACK if black else BEIGE
    eye = "#C8F04D" if black else INK
    ear = ORANGE if black else "#F2A0A0"
    parts = [
        P(HEAD, fill=fill, stroke=stroke, sw=4),
        P("M38,36 L40,44 L34,45 Z M70,36 L68,44 L74,45 Z", fill=ear),
        P(circle(43, 56, 4.2) + " " + circle(65, 56, 4.2), fill=eye),
        P(circle(44.5, 54.5, 1.3) + " " + circle(66.5, 54.5, 1.3), fill="#FFFFFF"),
        P("M51,64 L57,64 L54,67.5 Z", fill="#F2A0A0" if not black else "#FF8FA0"),
        P("M54,67.5 Q51,72 47,70 M54,67.5 Q57,72 61,70", stroke=stroke if black else INK, sw=2, cap="round"),
        P("M33,63 L22,61 M33,67 L22,69 M75,63 L86,61 M75,67 L86,69", stroke="#FFFFFF" if black else INK, sw=1.6, cap="round"),
    ]
    return G(parts, tx=cx - 54, ty=cy - 55, s=s, px=54, py=55)

def bg(color):
    return P("M0,0 L108,0 L108,108 L0,108 Z", fill=color)

VARIANTS = {
    "A": ("Wachlarz", [bg(YEL),
        note(54, 37, kind="10", rot=-22), note(54, 34, kind="20", rot=0), note(54, 37, kind="50", rot=22),
        cat(37, 67, 0.56), cat(71, 67, 0.56, black=True)]),
    "B": ("Nocny skarbiec", [bg(INK),
        note(54, 47, kind="100", rot=-8), note(54, 42, kind="50", rot=4), note(54, 37, kind="20", rot=-3),
        cat(34, 70, 0.52, stroke=YEL), cat(74, 70, 0.52, black=True, stroke=YEL)]),
    "C": ("Wspólny banknot", [bg(YEL),
        cat(35, 49, 0.5), cat(73, 49, 0.5, black=True),
        note(54, 66, w=52, h=28, kind="50", rot=-5),
        P(circle(37, 54, 3.6) + " " + circle(44, 54.5, 3.6), fill=BEIGE, stroke=INK, sw=1.6),
        P(circle(64, 55, 3.6) + " " + circle(71, 54.5, 3.6), fill=BLACK, stroke=INK, sw=1.6)]),
    "D": ("Deszcz hajsu", [bg(ORANGE),
        note(30, 28, w=20, h=12, kind="20", rot=-25), note(56, 22, w=20, h=12, kind="50", rot=15),
        note(80, 32, w=20, h=12, kind="10", rot=-10), note(44, 42, w=20, h=12, kind="100", rot=30),
        note(68, 46, w=20, h=12, kind="5", rot=-18),
        cat(38, 71, 0.54), cat(70, 71, 0.54, black=True)]),
}

def svg_node(n):
    if n["t"] == "p":
        a = [f'd="{n["d"]}"', f'fill="{n["fill"] or "none"}"']
        if n["stroke"]:
            a += [f'stroke="{n["stroke"]}"', f'stroke-width="{n["sw"]}"', 'stroke-linejoin="round"']
            if n["cap"]: a.append(f'stroke-linecap="{n["cap"]}"')
        return f"<path {' '.join(a)}/>"
    t = f'translate({n["tx"]},{n["ty"]}) translate({n["px"]},{n["py"]}) rotate({n["rot"]}) scale({n["s"]}) translate({-n["px"]},{-n["py"]})'
    return f'<g transform="{t}">' + "".join(svg_node(c) for c in n["c"]) + "</g>"

def to_svg(nodes, size=108):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">' + "".join(svg_node(n) for n in nodes) + "</svg>"

def android_color(c):
    c = c.lstrip("#")
    return "#" + (c[6:8] + c[:6] if len(c) == 8 else c)

def vec_node(n, ind="    "):
    if n["t"] == "p":
        a = [f'android:pathData="{n["d"]}"']
        if n["fill"]: a.append(f'android:fillColor="{android_color(n["fill"])}"')
        if n["stroke"]:
            a += [f'android:strokeColor="{android_color(n["stroke"])}"', f'android:strokeWidth="{n["sw"]}"', 'android:strokeLineJoin="round"']
            if n["cap"]: a.append(f'android:strokeLineCap="{n["cap"]}"')
        return f"{ind}<path " + f"\n{ind}    ".join(a) + " />\n"
    a = f'android:translateX="{n["tx"]}" android:translateY="{n["ty"]}" android:scaleX="{n["s"]}" android:scaleY="{n["s"]}" android:rotation="{n["rot"]}" android:pivotX="{n["px"]}" android:pivotY="{n["py"]}"'
    return f"{ind}<group {a}>\n" + "".join(vec_node(c, ind + "    ") for c in n["c"]) + f"{ind}</group>\n"

def to_vector(nodes):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + "".join(vec_node(n) for n in nodes) + "</vector>\n")

if __name__ == "__main__":
    if sys.argv[1] == "preview":
        cards = []
        for k, (name, nodes) in VARIANTS.items():
            inner = to_svg(nodes, 200)
            cards.append(f'<div class="c"><div class="row"><div class="sq">{inner}</div><div class="ci">{inner}</div>'
                         f'<div class="sm">{to_svg(nodes, 56)}</div></div><b>{k}. {name}</b></div>')
        print('<html><body style="margin:0;background:#2b2b2b;font:20px sans-serif;color:#fff;padding:20px">'
              '<style>.c{margin:0 0 26px}.row{display:flex;gap:22px;align-items:center;margin-bottom:8px}.sq svg{border-radius:36px}'
              '.ci svg{border-radius:50%}.sm svg{border-radius:14px}</style>' + "".join(cards) + "</body></html>")
    else:
        print(to_vector(VARIANTS[sys.argv[2]][1]))


def write_android(variant, res):
    """Zapisuje ikonę do projektu: pełny obrazek do aplikacji, warstwa przednia i kolor tła dla launchera."""
    import os, re
    nodes = VARIANTS[variant][1]
    background, fg = nodes[0], nodes[1:]
    with open(os.path.join(res, "drawable", "ic_cnc.xml"), "w") as f:
        f.write(to_vector(nodes))
    with open(os.path.join(res, "drawable", "ic_cnc_fg.xml"), "w") as f:
        f.write(to_vector(fg))
    colors = os.path.join(res, "values", "colors.xml")
    s = open(colors).read()
    line = f'    <color name="cnc_bg">{background["fill"]}</color>\n'
    s = re.sub(r'    <color name="cnc_bg">[^<]*</color>\n', "", s)
    s = s.replace("</resources>", line + "</resources>")
    open(colors, "w").write(s)


if __name__ == "__main__" and sys.argv[1] == "android":
    write_android(sys.argv[2], sys.argv[3])
