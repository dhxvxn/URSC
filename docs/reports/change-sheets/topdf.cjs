const { chromium } = require('playwright');
(async () => {
  const [IN, OUT] = process.argv.slice(2);
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto('file://' + IN, { waitUntil: 'networkidle' });
  await p.pdf({ path: OUT, format: 'A4', printBackground: true,
    margin: { top: '16mm', bottom: '16mm', left: '14mm', right: '14mm' } });
  await b.close();
  console.log('pdf written');
})().catch(e => { console.error('FAIL', e.message); process.exit(1); });
