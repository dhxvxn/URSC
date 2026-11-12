from pathlib import Path
import re,html,base64,json,hashlib,zipfile
from xml.sax.saxutils import escape
from PIL import Image as PILImage
from reportlab.platypus import SimpleDocTemplate,Paragraph,Spacer,Image,PageBreak,KeepTogether
from reportlab.lib.styles import getSampleStyleSheet,ParagraphStyle
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from docx import Document
from docx.shared import Inches,Pt
P=Path(__file__).resolve().parent
SCREENSHOT_COUNT=len(json.loads((P/'capture-evidence.json').read_text())['screenshots'])
md=(P/'GUI_COMPLETE_GUIDE.md').read_text()
blocks=md.split('\n\n')
def inline(t,fmt='html'):
 t=escape(t)
 t=re.sub(r'\*\*([^*]+)\*\*',r'<b>\1</b>',t)
 if fmt=='pdf':t=re.sub(r'`([^`]+)`',r'<font face="Courier">\1</font>',t)
 else:t=re.sub(r'`([^`]+)`',r'<code>\1</code>',t)
 return t
styles=getSampleStyleSheet();styles['BodyText'].fontSize=9;styles['BodyText'].leading=12
styles['Heading1'].fontSize=16;styles['Heading2'].fontSize=13;styles['Heading3'].fontSize=10
styles.add(ParagraphStyle(name='SmallCaption',parent=styles['BodyText'],fontSize=8,textColor=colors.HexColor('#475569')))
story=[];doc=Document();doc.styles['Normal'].font.size=Pt(10)
htmlparts=[];toc=[];heading_counter=0
for block in blocks:
 block=block.strip()
 if not block:continue
 image=re.match(r'^!\[([^]]*)\]\(([^)]+)\)$',block)
 heading=re.match(r'^(#{1,4}) (.*)$',block)
 if heading:
  level=len(heading[1]);title=heading[2];heading_counter+=1;anchor='section-'+str(heading_counter)
  if level<=3:toc.append((level,title,anchor))
  htmlparts.append(f'<h{level} id="{anchor}">{inline(title)}</h{level}>')
  style='Title' if level==1 else ['Heading1','Heading2','Heading3'][level-2]
  story.append(Paragraph(inline(title,'pdf'),styles[style]))
  if level==1:doc.add_heading(title,0)
  else:doc.add_heading(title.replace('`',''),level-1)
 elif image:
  caption,path=image.groups();f=P/path
  with PILImage.open(f) as im:w,h=im.size
  factor=min(500/w,450/h)
  story.append(Image(str(f),width=w*factor,height=h*factor))
  story.append(Paragraph('Full-resolution image: '+escape(path),styles['SmallCaption']))
  scale=min(6.2/w,6.0/h);doc.add_picture(str(f),width=Inches(w*scale));doc.add_paragraph('Full-resolution image: '+path,style='Caption')
  data=base64.b64encode(f.read_bytes()).decode()
  htmlparts.append('<figure><a href="'+path+'"><img loading="lazy" src="data:image/png;base64,'+data+'" alt="'+html.escape(caption,quote=True)+'"></a><figcaption>'+html.escape(caption)+' — click to open the original PNG when using the extracted package.</figcaption></figure>')
 elif re.match(r'^[-\d]',block) and all(re.match(r'^(?:-|\d+\.) ',l) for l in block.splitlines()):
  ordered=block[0].isdigit();tag='ol' if ordered else 'ul';htmlparts.append('<'+tag+'>')
  for line in block.splitlines():
   text=re.sub(r'^(?:-|\d+\.) ','',line)
   story.append(Paragraph('• '+inline(text,'pdf'),styles['BodyText']))
   doc.add_paragraph(re.sub(r'[`*]','',text),style='List Number' if ordered else 'List Bullet')
   htmlparts.append('<li>'+inline(text)+'</li>')
  htmlparts.append('</'+tag+'>')
 else:
  # Text paragraphs only; source/image links are plain readable paths in PDF/Word.
  text=block.replace('\n',' ')
  story.append(Paragraph(inline(text,'pdf'),styles['BodyText']))
  doc.add_paragraph(re.sub(r'[`*]','',text))
  htmlparts.append('<p>'+inline(text)+'</p>')
 story.append(Spacer(1,5))
def footer(c,d):
 c.setFont('Helvetica',8);c.setFillColor(colors.HexColor('#475569'));c.drawString(42,25,'URSC | Complete GUI control guide');c.drawRightString(553,25,str(d.page))
SimpleDocTemplate(str(P/'GUI_COMPLETE_GUIDE.pdf'),pagesize=A4,leftMargin=42,rightMargin=42,topMargin=40,bottomMargin=42).build(story,onFirstPage=footer,onLaterPages=footer)
doc.save(P/'GUI_COMPLETE_GUIDE.docx')
nav=''.join('<a class="level-'+str(level)+'" href="#'+anchor+'">'+html.escape(title.replace('`',''))+'</a>' for level,title,anchor in toc)
css='''body{margin:0;font:16px/1.6 system-ui,sans-serif;color:#182536;background:#f5f7fb}nav{position:fixed;top:0;bottom:0;left:0;width:265px;overflow:auto;background:#122234;color:#e7eff8;padding:18px;box-sizing:border-box}nav a{display:block;color:inherit;text-decoration:none;padding:5px 0;font-size:13px}nav .level-3{padding-left:12px;color:#b6ccdf}main{max-width:1050px;margin-left:285px;padding:28px 36px}h1,h2,h3,h4{scroll-margin-top:20px;line-height:1.3}h1{font-size:30px}h2{margin-top:50px;border-bottom:2px solid #9eb3ca;padding-bottom:8px}h3{margin-top:32px}h4{margin-top:25px;color:#154a75}p{margin:10px 0}code{font-size:.9em;background:#e5edf5;padding:2px 4px;border-radius:4px;overflow-wrap:anywhere}figure{margin:16px 0;background:white;padding:12px;border:1px solid #ccd8e5;border-radius:8px}img{width:100%;height:auto;display:block}figcaption{font-size:13px;color:#50647b;margin-top:8px}.intro{background:#dfeefc;border-left:4px solid #245d8b;padding:12px}a{color:#155e9f}@media(max-width:900px){nav{position:relative;width:auto;max-height:250px}main{margin:0;padding:20px}}@media print{nav{display:none}main{margin:0;max-width:none;padding:0}figure{break-inside:avoid}h2,h3,h4{break-after:avoid}body{background:white;font-size:10pt}}'''
page='<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>URSC complete GUI control guide</title><style>'+css+'</style><nav><h2>GUI guide</h2><p>Use Ctrl+F to find a field or control.</p>'+nav+'</nav><main><p class="intro">'+str(SCREENSHOT_COUNT)+' current screenshots · 85 settings · 124 other controls/interactions. All fixed parameter choices are listed. Images are embedded for offline reading.</p>'+''.join(htmlparts)+'</main></html>'
(P/'README.html').write_text(page)
(P/'README.md').write_text(f'''# Complete GUI presentation guide

- [Illustrated guide — PDF](GUI_COMPLETE_GUIDE.pdf)
- [Editable guide — Word](GUI_COMPLETE_GUIDE.docx)
- [Offline illustrated guide — HTML](README.html) (download and open in a browser)
- [GitHub-readable full guide](GUI_COMPLETE_GUIDE.md)
- [Full package](GUI_COMPLETE_GUIDE_PACKAGE.zip)
- [Dropdown checklist](DROPDOWN_INDEX.md)
- [Coverage audit](coverage-audit.json)

Includes {SCREENSHOT_COUNT} screenshots, all 85 parameter definitions, every one of their 34
fixed choices, and 124 additional controls/interactions across all seven tabs,
shared dialogs and the separate Guide page. The editable Word copy contains the
same explanations; PDF/HTML preserve rich inline emphasis. Native OS dialogs
are described because their appearance differs between Windows and Linux.

Screenshots of Saved presentation show actual archived campaign results.
Populated Results/History examples are clearly labelled SIMULATED documentation
runs; the one-date Window illustration is not the real two-month campaign.
The two-month campaign was not rerun. Full-resolution PNGs are in screenshots/.
See capture-evidence.json and coverage-audit.json for source/control traceability.
''')
# Check each PDF page has extractable text and every documented key survived export.
from pypdf import PdfReader
pdf=PdfReader(P/'GUI_COMPLETE_GUIDE.pdf');texts=[p.extract_text() or '' for p in pdf.pages]
alltext='\n'.join(texts)
params=json.loads((P/'parameter-reference.json').read_text())
assert all(x['key'] in alltext for x in params)
assert all(x.strip() for x in texts)
print('PDF pages:',len(pdf.pages),'HTML bytes:',len(page.encode()))
files=sorted(f for f in P.rglob('*') if f.is_file() and f.name not in ['GUI_COMPLETE_GUIDE_PACKAGE.zip','SHA256SUMS'])
(P/'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+str(f.relative_to(P))+'\n' for f in files))
with zipfile.ZipFile(P/'GUI_COMPLETE_GUIDE_PACKAGE.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in files+[P/'SHA256SUMS']:z.write(f,'URSC_GUI_GUIDE/'+str(f.relative_to(P)))
with zipfile.ZipFile(P/'GUI_COMPLETE_GUIDE_PACKAGE.zip') as z:assert z.testzip() is None
print('ZIP files:',len(files)+1,'bytes:',(P/'GUI_COMPLETE_GUIDE_PACKAGE.zip').stat().st_size)
