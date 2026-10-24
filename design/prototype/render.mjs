// Renderiza o protótipo em PNG com Playwright + Chromium e roda testes de fumaça por asserção.
// Uso: node design/prototype/render.mjs            (precisa de `playwright` resolvível: npm i -D playwright, ou PLAYWRIGHT_ROOT)
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";
import assert from "node:assert/strict";

const require = createRequire(process.env.PLAYWRIGHT_ROOT ? path.join(process.env.PLAYWRIGHT_ROOT, "/") : import.meta.url);
const { chromium } = require("playwright");

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, "shots");
fs.mkdirSync(out, { recursive: true });
const url = "file://" + path.join(here, "index.html");

const shots = [
  { name: "feed", hash: "#/" },
  { name: "feed-casos-uti-sem-resposta", hash: "#/", setup: async (p) => { await p.click('[data-tipo="caso"]'); await p.click('[data-esp="UTI"]'); await p.check("#sw-sem"); } },
  { name: "caso", hash: "#/caso/p2" },
  { name: "atores", hash: "#/atores" },
  { name: "perfil", hash: "#/perfil" },
  { name: "privacidade", hash: "#/privacidade" },
  { name: "privacidade-excluir", hash: "#/privacidade", setup: async (p) => { await p.click("#btn-del"); await p.check("#ck-entendo"); } },
  { name: "encarregado", hash: "#/encarregado" },
];

const browser = await chromium.launch();
let failures = 0;
for (const scheme of ["light", "dark"]) {
  for (const viewport of [{ w: 1280, h: 860, tag: "desktop" }, { w: 390, h: 844, tag: "mobile" }]) {
    for (const s of shots) {
      const page = await browser.newPage({ viewport: { width: viewport.w, height: viewport.h }, colorScheme: scheme, reducedMotion: "reduce", deviceScaleFactor: 1 });
      try {
        await page.goto(url + s.hash);
        await page.evaluate(() => localStorage.clear());
        await page.goto(url + s.hash);
        await page.waitForSelector("#main h1", { timeout: 5000 });
        if (s.setup) await s.setup(page);
        await page.waitForTimeout(50);
        await page.screenshot({ path: path.join(out, `${s.name}-${viewport.tag}-${scheme}.png`), fullPage: true });

        // fumaça: asserções mínimas por tela
        const h1 = await page.textContent("#main h1");
        assert.ok(h1 && h1.trim().length > 0, `${s.name}: h1 vazio`);
        if (s.name === "feed") {
          const n = await page.locator("#feed-list article").count();
          assert.ok(n >= 5, `feed: esperava ≥5 publicações, veio ${n}`);
          assert.equal(await page.getAttribute('[data-tipo="todos"]', "aria-selected"), "true");
        }
        if (s.name === "feed-casos-uti-sem-resposta") {
          const titles = await page.locator("#feed-list h3").allTextContents();
          assert.equal(titles.length, 1, `segmentação: esperava 1 caso UTI sem resposta, veio ${titles.length}`);
          assert.match(titles[0], /Choque séptico/);
        }
        if (s.name === "atores") assert.equal(await page.locator(".actor").count(), 8, "atores: esperava 8 cards");
        if (s.name === "privacidade") {
          const checked = await page.locator("[data-consent]:checked").count();
          assert.equal(checked, 0, "consentimentos devem começar desligados");
        }
        if (s.name === "privacidade-excluir") {
          assert.ok(await page.locator("#dlg-del[open]").count() === 1, "alertdialog de exclusão não abriu");
          assert.equal(await page.isDisabled("#dlg-confirm"), false, "botão deve habilitar depois da caixa Entendo");
        }
        // a11y básica: todo botão de ícone tem nome acessível; toda img é decorativa com alt=""
        const unnamed = await page.$$eval("button.ib, a.ib", (els) => els.filter((e) => !e.getAttribute("aria-label") && !e.textContent.trim()).length);
        assert.equal(unnamed, 0, `${s.name}: botão de ícone sem nome acessível`);
        const badImgs = await page.$$eval("img", (els) => els.filter((e) => e.getAttribute("alt") === null).length);
        assert.equal(badImgs, 0, `${s.name}: img sem alt`);
      } catch (e) {
        failures++;
        console.error(`✗ ${s.name} ${viewport.tag} ${scheme}: ${e.message}`);
      } finally {
        await page.close();
      }
    }
  }
}
await browser.close();
console.log(failures ? `${failures} falha(s)` : `ok · ${shots.length * 4} capturas em ${path.relative(process.cwd(), out)}`);
process.exit(failures ? 1 : 0);
