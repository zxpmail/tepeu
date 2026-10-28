// Channel-B collector v8: headed real Chrome + anti-detection — the "manual-equivalent" form
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const rawDir = process.argv[2];
const queries = fs.readFileSync(path.join(rawDir, 'queries.txt'), 'utf8')
  .split('\n').map(s => s.trim()).filter(Boolean);
const sleep = ms => new Promise(r => setTimeout(r, ms));

function decodeBing(href) {
  const s = href.replace(/&amp;/g, '&');
  const m = s.match(/[?&]u=a1([A-Za-z0-9\-_]+)/);
  if (!m) return (h => h && h.startsWith('http') && !/bing\.com/.test(h) ? h : null)(s);
  let b64 = m[1].replace(/-/g, '/').replace(/_/g, '=');
  while (b64.length % 4) b64 += '=';
  try { return Buffer.from(b64, 'base64').toString('utf8'); } catch { return null; }
}

(async () => {
  const browser = await chromium.launch({
    headless: false,
    channel: 'chrome',
    args: ['--disable-blink-features=AutomationControlled'],
  });
  const ctx = await browser.newContext({ viewport: null });
  const page = await ctx.newPage();
  const seen = new Set();
  let n = 0;
  for (const q of queries) {
    try {
      await page.goto(`https://www.bing.com/search?q=${encodeURIComponent(q)}&ensearch=1`, { timeout: 30000, waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(3500);
      const hrefs = await page.$$eval('h2 a', as => as.map(a => a.href));
      const urls = [...new Set(hrefs.map(decodeBing).filter(Boolean))].slice(0, 4);
      console.log(`[q] "${q.slice(0, 40)}..." -> ${urls.length} urls`);
      for (const u of urls) {
        if (seen.has(u)) continue;
        seen.add(u);
        n++;
        const host = new URL(u).hostname.replace(/[^a-zA-Z0-9\-]/g, '-');
        const slug = `raw-${n}-${host}`;
        try {
          const p2 = await ctx.newPage();
          await p2.goto(u, { timeout: 35000, waitUntil: 'domcontentloaded' });
          await p2.waitForTimeout(2500);
          const txt = await p2.evaluate(() => document.body ? document.body.innerText : '');
          await p2.close();
          if (!txt || txt.length < 400) { console.log(`SKIP-THIN: ${u} (len ${txt.length})`); n--; continue; }
          const body = txt.slice(0, 8000);
          const meta = `# RAW-${n}\n- URL: ${u}\n- Query: ${q}\n- Channel: B (headed real Chrome, manual-equivalent)\n- Date: ${new Date().toISOString().slice(0, 10)}\n\n---\n\n`;
          fs.writeFileSync(path.join(rawDir, slug + '.md'), meta + body, 'utf8');
          console.log(`SAVED: ${slug}.md <- ${u} (len ${body.length})`);
        } catch (e) { console.log(`DL-FAIL: ${u} -> ${e.message.slice(0, 80)}`); n--; }
        await sleep(2500);
      }
    } catch (e) { console.log(`SEARCH-FAIL: ${q} -> ${e.message.slice(0, 80)}`); }
    await sleep(4000);
  }
  await browser.close();
  console.log(`DONE total=${n} unique=${seen.size}`);
})();
