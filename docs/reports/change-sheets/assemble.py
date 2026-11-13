import json, io, sys
from pypdf import PdfReader, PdfWriter
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

S = '/tmp/claude-0/-home-user-URSC/61dc98cb-9bea-52d0-a2f6-f89d8472166c/scratchpad/cs'
OUT = sys.argv[1]
blocks = json.load(open(S + '/blocks.json'))
new = PdfReader(S + '/new.pdf')
font = 'Helvetica'
for f in ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']:
    try:
        pdfmetrics.registerFont(TTFont('DV', f)); font = 'DV'
    except Exception:
        pass
what = ["§9 Computational methods · §10 Software architecture · §11 Processing pipeline",
        "§13 results: §13a carrier-phase rebuild and new §13b five improvements",
        "§17 Limitations · §18 Running the software",
        "Glossary (end): new terms"]


def labels(b):
    a, z = b['old']
    n = b['new'][1] - b['new'][0] + 1
    out = []
    for k in range(n):
        if a + k <= z:
            out.append((str(a + k), f"replaces printed page {a + k}"))
        else:
            suffix = chr(ord('a') + (a + k - z - 1))
            prev = out[-1][0]
            out.append((f"{z}{suffix}", f"new page — insert after page {prev}"))
    return out


w = PdfWriter()
# cover
buf = io.BytesIO()
c = canvas.Canvas(buf, pagesize=A4)
W, H = A4
c.setFillColorRGB(0.06, 0.22, 0.42); c.rect(0, H - 40 * mm, W, 40 * mm, fill=1, stroke=0)
c.setFillColorRGB(1, 1, 1); c.setFont(font, 20)
c.drawString(18 * mm, H - 22 * mm, "Change sheets")
c.setFont(font, 11)
c.drawString(18 * mm, H - 31 * mm, "GNSS Based Orbit Determination and Trajectory Prediction — project report (26-page printing)")
c.setFillColorRGB(0.1, 0.1, 0.1); c.setFont(font, 10.5)
y = H - 55 * mm
for line in ["These sheets contain only the pages of the printed report whose content changed: the",
             "carrier-phase orbit determination on the Orekit engine and the five further improvements",
             "with their two-month results. All other printed pages stay exactly as they are.",
             "",
             "How to update the printed copy: for each block below, remove the printed pages listed and",
             "put the enclosed pages in their place. Each enclosed page is labelled in its top margin."]:
    c.drawString(18 * mm, y, line); y -= 5.5 * mm
y -= 4 * mm
c.setFont(font, 10.5)
c.setFillColorRGB(0.06, 0.22, 0.42)
c.drawString(18 * mm, y, "Remove printed pages"); c.drawString(62 * mm, y, "Insert enclosed pages"); c.drawString(108 * mm, y, "Content")
y -= 3 * mm; c.line(18 * mm, y, W - 18 * mm, y); y -= 6 * mm
c.setFillColorRGB(0.1, 0.1, 0.1)
total = 0
for i, b in enumerate(blocks):
    lab = [l for l, _ in labels(b)]
    old = f"{b['old'][0]}" if b['old'][0] == b['old'][1] else f"{b['old'][0]}–{b['old'][1]}"
    c.drawString(18 * mm, y, old)
    c.drawString(62 * mm, y, ", ".join(lab))
    txt = what[i] if i < len(what) else ""
    # wrap content column
    words, line, lines = txt.split(), "", []
    for wd in words:
        if c.stringWidth(line + " " + wd, font, 10.5) > (W - 18 * mm - 108 * mm):
            lines.append(line); line = wd
        else:
            line = (line + " " + wd).strip()
    lines.append(line)
    for k, l in enumerate(lines):
        c.drawString(108 * mm, y - k * 5 * mm, l)
    y -= max(1, len(lines)) * 5 * mm + 4 * mm
    total += len(lab)
y -= 4 * mm
c.setFont(font, 10)
oldn = sum(b['old'][1] - b['old'][0] + 1 for b in blocks)
for line in [f"{oldn} printed pages are replaced by {total} pages; the report grows from 26 to {26 - oldn + total} pages.",
             "Pages 1–8 (title, contents, Parts I–IV), 12–17, 19–20 and 23–25 are unchanged.",
             "The table of contents is unchanged: the new results are subsections §13a and §13b of §13.",
             "Where a block ends part-way down a page, the rest of that page is intentionally blank so",
             "that the next unchanged printed page follows on correctly."]:
    c.drawString(18 * mm, y, line); y -= 5.2 * mm
c.showPage(); c.save()
w.append(PdfReader(io.BytesIO(buf.getvalue())))
for b in blocks:
    for k, (lab, text) in enumerate(labels(b)):
        page = new.pages[b['new'][0] - 1 + k]
        pw, ph = float(page.mediabox.width), float(page.mediabox.height)
        ob = io.BytesIO()
        oc = canvas.Canvas(ob, pagesize=(pw, ph))
        oc.setFont(font, 8); oc.setFillColorRGB(0.75, 0.2, 0.1)
        oc.drawRightString(pw - 14 * mm, ph - 8 * mm, f"CHANGE SHEET · page {lab} · {text}")
        oc.save()
        page.merge_page(PdfReader(io.BytesIO(ob.getvalue())).pages[0])
        w.add_page(page)
w.write(OUT)
print("written", OUT, "pages", len(w.pages))
