"""Build change sheets: only the pages of the printed report that changed, ready to swap in.

Method
------
* old.pdf = exact re-render of the printed report (text-identical on all pages).
* The new report is rendered with the same pipeline. Before every "resync" element (an element
  that begins a page in the printed copy and lies after a changed block), a forced page break is
  inserted, so from that element on the new pages equal the printed ones.
* For every changed block: printed pages [a, b] are replaced by new pages [a', b'].
"""
import re, subprocess, sys, json, os

S = '/tmp/claude-0/-home-user-URSC/61dc98cb-9bea-52d0-a2f6-f89d8472166c/scratchpad'
CS = S + '/cs'
OLD_PDF = S + '/URSC-Report.pdf'


def pages_text(pdf):
    n = int(re.search(r'Pages:\s+(\d+)', subprocess.check_output(['pdfinfo', pdf], text=True)).group(1))
    return [subprocess.check_output(['pdftotext', '-f', str(p), '-l', str(p), pdf, '-'], text=True) for p in range(1, n + 1)]


def norm(t):
    return re.sub(r'\s+', ' ', t).strip()


def build(docs_dir, html, pdf, breaks):
    subprocess.check_call([CS + '/build.sh', docs_dir, html, pdf + '.tmp.pdf'])
    if breaks:
        # inject forced page breaks before the elements whose text starts with a resync phrase
        h = open(html, encoding='utf-8').read()
        for phrase in breaks:
            i = h.find(phrase)
            if i < 0:
                raise SystemExit('resync phrase not found in new HTML: ' + phrase)
            # walk back to the opening tag of the block element (p, h2, h3, li, figure, div, table)
            m = None
            for mm in re.finditer(r'<(p|h1|h2|h3|li|figure|div|table|ul|ol)(\s[^>]*)?>', h[:i]):
                m = mm
            tag = m
            ins = tag.start()
            h = h[:ins] + '<div style="page-break-before:always;break-before:page"></div>' + h[ins:]
        open(html, 'w', encoding='utf-8').write(h)
    subprocess.check_call(['node', S + '/topdf.cjs', html, pdf], cwd=S, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    cfg = json.load(open(sys.argv[1]))
    old = pages_text(OLD_PDF)
    build(cfg['docs'], CS + '/new.html', CS + '/new.pdf', cfg.get('resync', []))
    new = pages_text(CS + '/new.pdf')
    print('printed pages', len(old), 'new pages', len(new))
    # align: walk both documents; identical pages are kept, differing runs form blocks
    blocks = []
    i = j = 0
    while i < len(old) and j < len(new):
        if norm(old[i]) == norm(new[j]):
            i += 1
            j += 1
            continue
        # find the next page where they agree again
        best = None
        for di in range(0, len(old) - i + 1):
            for dj in range(0, len(new) - j + 1):
                if i + di < len(old) and j + dj < len(new) and norm(old[i + di]) == norm(new[j + dj]) and (di or dj):
                    best = (di, dj)
                    break
            if best:
                break
        if not best:
            best = (len(old) - i, len(new) - j)
        blocks.append({'old': [i + 1, i + best[0]], 'new': [j + 1, j + best[1]]})
        i += best[0]
        j += best[1]
    if i < len(old) or j < len(new):
        blocks.append({'old': [i + 1, len(old)], 'new': [j + 1, len(new)]})
    print(json.dumps(blocks, indent=1))
    json.dump(blocks, open(CS + '/blocks.json', 'w'))
