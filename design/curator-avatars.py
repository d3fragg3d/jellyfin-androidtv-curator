"""Generates Curator user avatars as both preview SVGs and Android VectorDrawables.

Each avatar is a list of flat shapes on a 100x100 canvas. The gradient background is
painted by Compose at runtime (VectorDrawable gradients need API 24, minSdk is 21), so
it's only included in the preview SVG.
"""
import os
import re
import sys

# Usage: python3 design/curator-avatars.py <svg preview dir> app/src/main/res/drawable
OUT = sys.argv[1]
RES = sys.argv[2] if len(sys.argv) > 2 else None


def circle(cx, cy, r):
	return ellipse(cx, cy, r, r)


def ellipse(cx, cy, rx, ry):
	return f"M{cx - rx},{cy}a{rx},{ry} 0 1,0 {2 * rx},0a{rx},{ry} 0 1,0 {-2 * rx},0Z"


def fill(d, color, alpha=1.0):
	return {"d": d, "fill": color, "alpha": alpha}


def stroke(d, color, width, alpha=1.0):
	return {"d": d, "stroke": color, "width": width, "alpha": alpha}


INK = "#2A2238"
SKIN = "#F4C9A6"
SKIN_SHADE = "#E2AC86"
BLUSH = "#F08F8F"


def bust(shirt, collar=None, shade=SKIN_SHADE):
	parts = [
		fill("M12,100C12,82 28,74 50,74C72,74 88,82 88,100Z", shirt),
		fill("M43,60L57,60L57,76C54,80 46,80 43,76Z", shade),
	]
	if collar:
		parts.append(fill("M41,75L50,84L59,75C56,78 44,78 41,75Z", collar))
	return parts


def head(skin=SKIN):
	return [
		fill(circle(33, 49, 4), skin),
		fill(circle(67, 49, 4), skin),
		fill(ellipse(50, 46, 17, 20), skin),
	]


def face(smile_y=57, eyes_y=47, blush=True):
	parts = [
		fill(circle(43.5, eyes_y, 2), INK),
		fill(circle(56.5, eyes_y, 2), INK),
		stroke(f"M45,{smile_y}Q50,{smile_y + 4} 55,{smile_y}", INK, 1.8),
	]
	if blush:
		parts += [fill(circle(39, eyes_y + 6, 2.6), BLUSH, 0.45), fill(circle(61, eyes_y + 6, 2.6), BLUSH, 0.45)]
	return parts


def glasses(color=INK, r=5.2):
	return [
		stroke(circle(43.5, 47, r), color, 1.6),
		stroke(circle(56.5, 47, r), color, 1.6),
		stroke("M48.7,47Q50,45.8 51.3,47", color, 1.6),
	]


def girl(cx, cy, s, older):
	"""A little girl with brown hair. Paths are drawn around the head centre, then placed at (cx, cy) with scale s."""
	def at(d):
		return re.sub(r"(-?[\d.]+),(-?[\d.]+)", lambda m: f"{cx + float(m[1]) * s:.2f},{cy + float(m[2]) * s:.2f}", d)

	def dot(x, y, r):
		return circle(round(cx + x * s, 2), round(cy + y * s, 2), round(r * s, 2))

	hair, hair_light = ("#5A3524", "#6F4530") if older else ("#6B4028", "#82522F")
	dress, trim = ("#7B2CBF", "#E0AAFF") if older else ("#E5487D", "#FFD6E5")
	parts = []
	if older:
		parts.append(fill(at("M-20,-2C-22,-24 -10,-26 0,-26C10,-26 22,-24 20,-2C22,12 24,28 23,40C14,44 -14,44 -23,40C-24,28 -22,12 -20,-2Z"), hair))
	else:
		parts += [
			fill(at("M-17,-8C-28,-12 -32,2 -27,12C-25,16 -21,14 -20,10C-19,4 -17,0 -15,-2Z"), hair),
			fill(at("M17,-8C28,-12 32,2 27,12C25,16 21,14 20,10C19,4 17,0 15,-2Z"), hair),
			fill(dot(-17, -7, 3), trim),
			fill(dot(17, -7, 3), trim),
		]
	parts += [
		fill(at("M-20,70C-20,30 -12,24 0,24C12,24 20,30 20,70Z"), dress),
		fill(at("M-6,14L6,14L6,26C4,29 -4,29 -6,26Z"), SKIN_SHADE),
		fill(at("M-8,25L0,32L8,25C5,27 -5,27 -8,25Z"), trim),
		fill(dot(-17, 2, 3.4), SKIN),
		fill(dot(17, 2, 3.4), SKIN),
		fill(ellipse(cx, round(cy + (0 if older else 1) * s, 2), round(17 * s, 2), round((19 if older else 17) * s, 2)), SKIN),
	]
	if older:
		parts += [
			fill(at("M-17.5,2C-19,-18 -9,-21 0,-21C9,-21 19,-18 17.5,2C15,-7 8,-11 -3,-11C-9,-10 -15,-6 -17.5,2Z"), hair_light),
			stroke(at("M-14,-13Q0,-24 14,-13"), trim, 2 * s),
		]
	else:
		parts.append(fill(at("M-17.5,0C-18,-15 -8,-18 0,-18C8,-18 18,-15 17.5,0C14,-4 12,-8 8,-9C5,-6 1,-6 -2,-9C-7,-8 -14,-5 -17.5,0Z"), hair_light))
	eye_y, eye_x, eye_r = (2, 6.5, 2.2) if older else (3, 6.5, 2.6)
	smile = "M-4.5,10Q0,14.5 4.5,10" if older else "M-3.5,10.5Q0,14.5 3.5,10.5"
	parts += [
		fill(dot(-eye_x, eye_y, eye_r), INK),
		fill(dot(eye_x, eye_y, eye_r), INK),
		fill(dot(-eye_x + 0.7, eye_y - 0.8, 0.7), "#FFFFFF"),
		fill(dot(eye_x + 0.7, eye_y - 0.8, 0.7), "#FFFFFF"),
		fill(dot(-11, 8, 2.8), BLUSH, 0.5),
		fill(dot(11, 8, 2.8), BLUSH, 0.5),
		stroke(at(smile), INK, 1.8 * s),
	]
	return parts


AVATARS = {
	# Dad — silver hair and full beard, round glasses
	"pthek": {
		"bg": ("#1E3C72", "#2A9D8F"),
		"shapes": bust("#264653", "#E9ECEF") + head() + [
			fill("M33,44C32,27 42,24 50,24C58,24 68,27 67,44C64,36 58,32 50,32C42,32 36,36 33,44Z", "#D5DAE1"),
			fill("M33,46C33,64 41,70 50,70C59,70 67,64 67,46C65,54 61,57 57,57L43,57C39,57 35,54 33,46Z", "#D5DAE1"),
			fill("M42,56C45,54 55,54 58,56C55,58 45,58 42,56Z", "#BCC3CC"),
		] + face(smile_y=60, blush=False) + glasses(),
	},
	# Mam — auburn bob with a side fringe, gold earrings, plum glasses
	"jthek": {
		"bg": ("#B5179E", "#F72585"),
		"shapes": bust("#7209B7", "#F4C9A6") + [
			fill("M29,52C26,30 38,22 50,22C62,22 74,30 71,52L72,66C66,69 61,67 60,64L60,40L40,40L40,64C39,67 34,69 28,66Z", "#9C3D1C"),
		] + head() + [
			fill("M33,46C32,30 42,25 51,25C61,25 68,31 67,44C62,36 52,32 44,36C39,38 35,41 33,46Z", "#B8512A"),
			fill(circle(33, 55, 2.2), "#FFC93C"),
			fill(circle(67, 55, 2.2), "#FFC93C"),
		] + face() + glasses("#5B1F4A"),
	},
	# Chris (admin) — short dark quiff, beard and on-brand headphones
	"admin": {
		"bg": ("#AA5CC3", "#00A4DC"),
		"shapes": bust("#1F1B2E", "#00A4DC") + head() + [
			fill("M33,44C31,28 41,22 52,23C63,24 69,32 67,44C64,37 59,34 52,34C45,34 38,37 33,44Z", "#3B2A22"),
			fill("M44,25C48,19 58,19 62,24C57,23 50,24 44,25Z", "#3B2A22"),
			fill("M33,46C33,64 41,69 50,69C59,69 67,64 67,46C65,53 61,56 57,56L43,56C39,56 35,53 33,46Z", "#3B2A22"),
			fill("M43,55.5C46,53.5 54,53.5 57,55.5C54,57 46,57 43,55.5Z", "#2C1F19"),
		] + face(blush=False) + [
			stroke("M45.5,59.5Q50,62.5 54.5,59.5", "#E89A8A", 1.8),
			stroke("M30,47C30,19 70,19 70,47", INK, 3.4),
			fill("M26,44Q26,41 29,41L33,41Q35,41 35,44L35,55Q35,58 33,58L29,58Q26,58 26,55Z", INK),
			fill("M65,44Q65,41 67,41L71,41Q74,41 74,44L74,55Q74,58 71,58L67,58Q65,58 65,55Z", INK),
			fill("M28,45L32,45L32,54L28,54Z", "#00A4DC"),
			fill("M68,45L72,45L72,54L68,54Z", "#AA5CC3"),
		],
	},
	# Gemma — long chestnut hair with a top bun
	"gemma": {
		"bg": ("#FF6B6B", "#FFA94D"),
		"shapes": [
			fill("M28,50C26,30 38,24 50,24C62,24 74,30 72,50L74,82C64,86 36,86 26,82Z", "#5A3220"),
		] + bust("#2B9348", "#F4C9A6") + head() + [
			fill(circle(50, 21, 8), "#5A3220"),
			fill("M33,46C32,30 42,26 50,26C58,26 68,30 67,46C63,38 56,33 50,33C44,33 37,38 33,46Z", "#6E4029"),
			stroke("M44,28Q50,25 56,28", "#8A5434", 1.4),
		] + face(),
	},
	# Stephen (username steven) — dark side-parted hair and stubble
	"steven": {
		"bg": ("#0F766E", "#65A30D"),
		"shapes": bust("#1E40AF", "#F4C9A6") + head() + [
			fill("M33,46C33,62 41,68 50,68C59,68 67,62 67,46C65,56 60,60 50,60C40,60 35,56 33,46Z", "#3A281C", 0.35),
			fill("M33,44C31,27 43,23 51,23C61,23 69,29 67,44C66,36 60,31 46,33C41,34 36,38 33,44Z", "#4A3426"),
			fill("M46,33C52,27 62,27 66,36C60,31 53,31 46,33Z", "#2F2018"),
		] + face(blush=False),
	},
	# Kids — Neve and Livvy: the 9-year-old behind with long hair, the 4-year-old in front with bunches
	"kids": {
		"bg": ("#FFD60A", "#FF8C42"),
		"shapes": girl(36, 40, 0.86, older=True) + girl(66, 60, 0.72, older=False),
	},
}


def svg(name, a):
	c0, c1 = a["bg"]
	out = [
		'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="300" height="300">',
		f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{c0}"/><stop offset="1" stop-color="{c1}"/></linearGradient>',
		'<clipPath id="c"><circle cx="50" cy="50" r="50"/></clipPath></defs>',
		'<g clip-path="url(#c)"><rect width="100" height="100" fill="url(#g)"/>',
	]
	for s in a["shapes"]:
		if "fill" in s:
			out.append(f'<path d="{s["d"]}" fill="{s["fill"]}" fill-opacity="{s["alpha"]}"/>')
		else:
			out.append(f'<path d="{s["d"]}" fill="none" stroke="{s["stroke"]}" stroke-width="{s["width"]}" stroke-linecap="round" stroke-opacity="{s["alpha"]}"/>')
	out.append("</g></svg>")
	return "\n".join(out)


def vector(name, a):
	out = [
		'<?xml version="1.0" encoding="utf-8"?>',
		f'<!-- Curator user avatar ({name}) — generated; the gradient background is drawn in Compose -->',
		'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
		'\tandroid:width="100dp"',
		'\tandroid:height="100dp"',
		'\tandroid:viewportWidth="100"',
		'\tandroid:viewportHeight="100">',
	]
	for s in a["shapes"]:
		if "fill" in s:
			attrs = [f'android:fillColor="{s["fill"]}"']
			if s["alpha"] != 1.0:
				attrs.append(f'android:fillAlpha="{s["alpha"]}"')
		else:
			attrs = [
				f'android:strokeColor="{s["stroke"]}"',
				f'android:strokeWidth="{s["width"]}"',
				'android:strokeLineCap="round"',
			]
			if s["alpha"] != 1.0:
				attrs.append(f'android:strokeAlpha="{s["alpha"]}"')
		attrs.append(f'android:pathData="{s["d"]}"')
		out.append("\t<path\n\t\t" + "\n\t\t".join(attrs) + " />")
	out.append("</vector>")
	return "\n".join(out) + "\n"


os.makedirs(OUT, exist_ok=True)
for name, a in AVATARS.items():
	with open(os.path.join(OUT, f"{name}.svg"), "w") as f:
		f.write(svg(name, a))
	if RES:
		with open(os.path.join(RES, f"avatar_{name}.xml"), "w") as f:
			f.write(vector(name, a))
	print(name, "->", ", ".join(a["bg"]))
