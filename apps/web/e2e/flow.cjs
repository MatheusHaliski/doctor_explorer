/*
 * Teste ponta a ponta no navegador: web real (Next) + core-api (perfil dev) + PostgreSQL + Valkey.
 * Login de verdade, sem mock: cadastro → código do mailer dev (lido do log da API) → selo CRM/RQE na fonte
 * simulada → grupo → caso bloqueado por identificador → caso publicado → feed por eixos → LGPD → sudo →
 * tema escuro → mobile → logout → login.
 *
 * Uso: API_LOG=<log da core-api> BASE=http://localhost:3000 [SHOTS=dir] [PLAYWRIGHT_ROOT=<node_modules>] node e2e/flow.cjs
 */
const fs = require("fs");
const path = require("path");
const { createRequire } = require("module");
const req = createRequire(process.env.PLAYWRIGHT_ROOT ? path.join(process.env.PLAYWRIGHT_ROOT, "/") : __filename);
const { chromium } = req("playwright");
const BASE = process.env.BASE || "http://localhost:3000";
const API_LOG = process.env.API_LOG || path.join(__dirname, "api.log");
const SHOTS = process.env.SHOTS || "";
const n = Date.now() % 100000;
const email = `e2e+${n}@example.org`;
const password = "frase longa e segura 2026";
const steps = [];
const errors = []; // erros de JS e console.error do navegador
const ok = (name, cond, extra = "") => { steps.push(`${cond ? "✓" : "✗"} ${name}${extra ? " · " + extra : ""}`); if (!cond) throw new Error("FALHOU: " + name + " " + extra); };

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 860 }, locale: "pt-BR" });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => errors.push(String(e)));
  // 4xx esperados (422 de validação, 401 de senha errada) aparecem como "Failed to load resource": não são erro da página
  page.on("console", (m) => { if (m.type() === "error" && !m.text().startsWith("Failed to load resource")) errors.push(`[${page.url()}] ${m.text()}`); });
  if (SHOTS) fs.mkdirSync(SHOTS, { recursive: true });
  const shot = (name) => (SHOTS ? page.screenshot({ path: path.join(SHOTS, name + ".png"), fullPage: true }) : Promise.resolve());

  await page.goto(BASE + "/");
  await page.waitForURL("**/login");
  ok("raiz redireciona para /login", true);
  await shot("01-login");

  // cadastro
  await page.goto(BASE + "/cadastro");
  await page.getByRole("button", { name: "Criar conta" }).click();
  await page.locator(".notice.danger").waitFor();
  ok("cadastro vazio devolve 422 agrupado", /campos para corrigir/.test(await page.locator(".notice.danger").innerText()));
  await shot("02-cadastro-422");
  await page.fill("#displayName", "Dra. Ana Ribeiro");
  await page.fill("#email", email);
  await page.fill("#birthDate", "1985-03-10");
  await page.fill("#password", password);
  await page.check("#acceptTerms");
  await page.check("#acceptPrivacy");
  await page.getByRole("button", { name: "Criar conta" }).click();
  await page.waitForURL("**/verificar-email");
  ok("cadastro → verificar e-mail", true);

  // código no log do mailer dev
  await new Promise((r) => setTimeout(r, 800));
  const log = fs.readFileSync(API_LOG, "utf8");
  const idx = log.lastIndexOf(`para=${email}`);
  const code = (log.slice(idx).match(/\b(\d{6})\b/) || [])[1];
  ok("código de 6 dígitos no mailer dev", !!code, code);
  await page.fill("#code", code);
  await page.getByRole("button", { name: "Confirmar" }).click();
  await page.waitForURL("**/perfil?novo=1");
  ok("e-mail confirmado → perfil", true);
  await shot("03-perfil-sem-selo");

  // selo CRM/RQE (fonte simulada: prefixo 12 = regular)
  await page.fill("#crmNumber", "12" + String(n).padStart(4, "0").slice(-4));
  await page.selectOption("#crmUf", "PR");
  await page.getByRole("button", { name: "Adicionar especialidade" }).click();
  await page.selectOption("#spec-0", "UTI");
  await page.fill("#rqe-0", "11111");
  await page.getByRole("button", { name: "Enviar para verificação" }).click();
  await page.getByText("CRM verificado na fonte oficial").waitFor({ timeout: 15000 });
  ok("selo VERIFIED após envio", true);
  await shot("04-perfil-verificado");

  // grupos
  await page.goto(BASE + "/grupos");
  const uti = page.locator(".card", { hasText: "Medicina intensiva" });
  await uti.getByRole("button", { name: "Entrar" }).click();
  await page.getByText("Você entrou no grupo.").waitFor();
  ok("entrada automática no grupo com RQE", true);
  await shot("05-grupos");

  // publicar caso: bloqueio por identificador
  await page.goto(BASE + "/casos/novo");
  await page.waitForSelector("#specialtyCode option", { state: "attached" });
  await page.fill("#title", "Choque séptico no paciente João da Silva");
  await page.fill("#complaint", "CPF 123.456.789-09, internado em 12/03/2024");
  await page.fill("#question", "Corticoide?");
  await page.getByRole("button", { name: "Publicar no grupo" }).click();
  await page.locator(".notice.danger").waitFor();
  const alertText = await page.locator(".notice.danger").innerText();
  ok("identificadores bloqueiam com 422 listando trechos", /NOME_PACIENTE|CPF|DATA_COMPLETA/.test(alertText), alertText.slice(0, 80));
  await shot("06-caso-bloqueado");
  const title = `Choque séptico refratário, 60 anos, sem foco ${n}`;
  await page.fill("#title", title);
  await page.fill("#complaint", "Vasopressor em dose alta há 36 h, lactato em queda lenta.");
  await page.fill("#question", "Alguém usaria corticoide neste ponto?");
  await page.fill("#tags", "sepse, vasopressor");
  await page.getByRole("button", { name: "Publicar no grupo" }).click();
  await page.waitForURL(/\/casos\/[0-9a-f-]{36}$/);
  ok("caso publicado → página do caso", true);
  await shot("07-caso");

  // feed segmentado
  await page.goto(BASE + "/feed");
  await page.getByText(title).waitFor();
  await page.getByRole("tab", { name: "Educação" }).click();
  await page.waitForURL("**/feed?kind=EDUCATION");
  await page.getByText("Nenhuma publicação com esses filtros.").waitFor();
  ok("filtro por tipo vai para a URL", (await page.getByText(title).count()) === 0, "caso some em Educação");
  await page.getByRole("tab", { name: "Caso clínico" }).click();
  await page.getByText(title).waitFor();
  await page.getByLabel("Só sem resposta").check();
  await page.waitForURL("**/feed?kind=CASE&unanswered=true");
  await page.getByText(/^\d+ publicaç/).waitFor();
  await page.getByText(title).waitFor();
  ok("só sem resposta mantém o caso", true);
  await shot("08-feed");

  // privacidade
  await page.goto(BASE + "/privacidade");
  await page.locator("#c-LOCATION").check();
  await page.getByText("Localização: ligado.").waitFor();
  ok("consentimento LOCATION ligado com toast", true);
  await page.getByRole("button", { name: "Ver meus dados" }).click();
  await page.locator("pre.json").waitFor();
  ok("seus dados renderizados", (await page.locator("pre.json").innerText()).includes(email));
  await shot("09-privacidade");

  // segurança: sudo e sessões
  await page.goto(BASE + "/seguranca");
  await page.fill("#sudo-password", password);
  await page.getByRole("button", { name: "Confirmar" }).click();
  await page.getByText("Identidade confirmada até").waitFor();
  ok("sudo concedido", true);
  ok("sessão atual listada", (await page.getByText("este aparelho").count()) === 1);
  await shot("10-seguranca");

  // diretório (público)
  await page.goto(BASE + "/instituicoes");
  await page.getByText(/instituiç/).first().waitFor();
  await shot("11-instituicoes");

  // tema escuro
  await page.getByRole("button", { name: "Alternar tema escuro" }).click();
  ok("tema escuro aplicado", (await page.evaluate(() => document.documentElement.dataset.theme)) === "dark");
  await page.goto(BASE + "/feed");
  await shot("12-feed-escuro");

  // mobile
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(BASE + "/feed");
  await page.getByRole("button", { name: "Abrir menu" }).click();
  await page.getByRole("link", { name: "Privacidade" }).waitFor();
  await shot("13-mobile-menu");

  // logout
  await page.setViewportSize({ width: 1280, height: 860 });
  await page.goto(BASE + "/feed");
  await page.getByRole("button", { name: "Sair" }).click();
  await page.waitForURL("**/login");
  await page.goto(BASE + "/feed");
  await page.waitForURL("**/login");
  ok("logout e guarda de sessão", true);

  // login com a conta criada
  await page.fill("#email", email);
  await page.fill("#password", "senha errada mesmo");
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.getByText("E-mail ou senha incorretos").waitFor();
  ok("mensagem neutra de login", true);
  await page.fill("#password", password);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL("**/feed");
  ok("login ok → feed", true);

  ok("sem erros de JS nem console.error", errors.length === 0, errors.join(" | ").slice(0, 1500));
  console.log(steps.join("\n"));
  await browser.close();
})().catch(async (e) => {
  console.log(steps.join("\n"));
  for (const err of errors) console.error("console.error:", err);
  console.error(e.message.slice(0, 200));
  process.exit(1);
});
