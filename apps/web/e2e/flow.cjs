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

/* ---- utilitários: TOTP (RFC 6238) para o login do moderador e chamadas à API a partir da página ---- */
const crypto = require("crypto");
function base32Decode(str) {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "", out = [];
  for (const ch of str.replace(/=+$/, "").toUpperCase()) bits += A.indexOf(ch).toString(2).padStart(5, "0");
  for (let i = 0; i + 8 <= bits.length; i += 8) out.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(out);
}
function totp(secretBase32, t = Math.floor(Date.now() / 1000)) {
  const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(Math.floor(t / 30)));
  const h = crypto.createHmac("sha1", base32Decode(secretBase32)).update(counter).digest();
  const o = h[h.length - 1] & 0xf;
  const code = ((h[o] & 0x7f) << 24 | h[o + 1] << 16 | h[o + 2] << 8 | h[o + 3]) % 1_000_000;
  return String(code).padStart(6, "0");
}
/** POST na API usando a sessão e o XSRF-TOKEN do próprio navegador (mesmo caminho que o app usa). */
async function apiPost(page, path, body) {
  return page.evaluate(async ({ path, body }) => {
    const token = document.cookie.split(";").map((c) => c.trim()).find((c) => c.startsWith("XSRF-TOKEN="))?.slice(11);
    const res = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": decodeURIComponent(token || "") }, body: JSON.stringify(body ?? {}) });
    return { status: res.status, body: await res.text() };
  }, { path, body });
}
async function registerVerified(ctx, { name, email, crm, specialties }) {
  const p = await ctx.newPage();
  await p.goto(BASE + "/cadastro");
  await p.fill("#displayName", name); await p.fill("#email", email); await p.fill("#birthDate", "1988-07-01"); await p.fill("#password", password);
  await p.check("#acceptTerms"); await p.check("#acceptPrivacy");
  await p.getByRole("button", { name: "Criar conta" }).click();
  await p.waitForURL("**/verificar-email");
  await new Promise((r) => setTimeout(r, 800));
  const log = fs.readFileSync(API_LOG, "utf8");
  const code = (log.slice(log.lastIndexOf(`para=${email}`)).match(/\b(\d{6})\b/) || [])[1];
  await p.fill("#code", code); await p.getByRole("button", { name: "Confirmar" }).click();
  await p.waitForURL("**/perfil?novo=1");
  await p.fill("#crmNumber", crm); await p.selectOption("#crmUf", "PR");
  for (let i = 0; i < specialties.length; i++) {
    await p.getByRole("button", { name: "Adicionar especialidade" }).click();
    await p.selectOption(`#spec-${i}`, specialties[i][0]); await p.fill(`#rqe-${i}`, specialties[i][1]);
  }
  await p.getByRole("button", { name: "Enviar para verificação" }).click();
  await p.getByText("Dados enviados.").waitFor();
  return p;
}

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

  // ---- RF11 · instituição: cadastro conferido (fakes: CNPJ 12…, CNES 12… coerentes), gestão, protocolo no feed ----
  const suffix = String(n).padStart(5, "0").slice(-5);
  const cnes = "12" + suffix, cnpj = "12" + (suffix + "000000000000").slice(0, 12);
  await page.goto(BASE + "/instituicoes/nova");
  await page.fill("#legalName", "Hospital Santa Clara LTDA"); await page.fill("#tradeName", `Hospital Santa Clara ${n}`);
  await page.fill("#cnpj", cnpj); await page.fill("#cnes", cnes); await page.fill("#city", "Curitiba");
  await page.getByRole("button", { name: "Cadastrar e conferir" }).click();
  await page.waitForURL(/\/instituicoes\/[0-9a-f-]{36}\/gestao$/);
  const hospitalId = page.url().match(/instituicoes\/([0-9a-f-]{36})/)[1];
  ok("instituição verificada → quem cadastra vira gestor", /Gestor do hospital/.test(await page.locator(".lead").innerText()));
  await shot("14-gestao");
  const protocolo = `Protocolo de sepse v3 ${n}`;
  await page.fill("#title", protocolo); await page.fill("#body", "Bundle de 1 hora: lactato, culturas, antibiótico, volume.");
  await page.getByRole("button", { name: "Publicar" }).click();
  await page.getByText("Publicado no feed de quem tem vínculo ativo.").waitFor();
  await page.goto(BASE + "/feed?origin=instituicao");
  await page.getByText(protocolo).waitFor();
  ok("protocolo institucional no feed com origem instituição", true);

  // Bruno (CRM 65…: Cardiologia RQE 33333) pede vínculo no hospital e entrada no grupo DERM (sem RQE → fila do moderador)
  const brunoCtx = await browser.newContext({ viewport: { width: 1280, height: 860 }, locale: "pt-BR" });
  const brunoEmail = `bruno+${n}@example.org`;
  const bruno = await registerVerified(brunoCtx, { name: "Bruno Tavares", email: brunoEmail, crm: "65" + String(n).padStart(4, "0").slice(-4), specialties: [["CARDIO", "33333"]] });
  const brunoId = (await bruno.evaluate(() => fetch("/api/v1/me").then((r) => r.json()))).id;
  await bruno.goto(BASE + "/instituicoes/" + hospitalId);
  await bruno.getByRole("button", { name: "Pedir vínculo / aceitar convite" }).click();
  await bruno.getByText("Pedido enviado ao gestor da instituição.").waitFor();
  await bruno.goto(BASE + "/grupos");
  await bruno.locator(".card", { hasText: "Dermatologia" }).getByRole("button", { name: "Entrar" }).click();
  await bruno.getByText("Pedido enviado ao moderador do grupo.").waitFor();
  ok("Bruno: vínculo pedido e entrada em grupo sem RQE fica pendente", true);

  // Ana aceita o vínculo na gestão; o perfil de Bruno mostra o convite para outras instituições que ela gere
  await page.goto(BASE + `/instituicoes/${hospitalId}/gestao`);
  const row = page.locator(".item", { hasText: "Bruno Tavares" });
  await row.getByRole("button", { name: "Aceitar" }).click();
  await page.getByText("agora faz parte do corpo clínico").waitFor();
  ok("gestora aceita o pedido de vínculo", true);
  await page.goto(BASE + "/perfil/" + brunoId);
  await page.getByText("Convidar para o corpo clínico").waitFor();
  ok("perfil de colega mostra convite para quem gere instituição", true);

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

  // ---- RF02 (US06) · moderação: Carla fica em revisão manual; Ana vira moderadora com 2FA e decide na fila ----
  const carlaCtx = await browser.newContext({ viewport: { width: 1280, height: 860 }, locale: "pt-BR" });
  const carla = await registerVerified(carlaCtx, { name: "Carla Nova", email: `carla+${n}@example.org`, crm: "00" + String(n).padStart(4, "0").slice(-4), specialties: [] });
  await carla.getByText("Em análise: a fonte do CFM ainda não respondeu").waitFor();
  const carlaId = (await carla.evaluate(() => fetch("/api/v1/me").then((r) => r.json()))).id;
  const mr = await apiPost(carla, "/api/v1/dev/credentials/manual-review"); // simula as 24 h sem resposta
  ok("Carla em revisão manual (atalho do perfil dev)", mr.status === 200 && mr.body.includes("MANUAL_REVIEW"), mr.body.slice(0, 80));

  const grant = await apiPost(page, "/api/v1/dev/roles/moderator");
  ok("papel MODERATOR concedido a Ana (perfil dev)", grant.status === 200);
  await page.goto(BASE + "/seguranca");
  await page.fill("#sudo-password", password);
  await page.getByRole("button", { name: "Confirmar" }).click();
  await page.getByText("Identidade confirmada até").waitFor();
  await page.getByRole("button", { name: "Ativar 2FA" }).click();
  const secret = (await page.locator("code").innerText()).trim();
  await page.fill("#confirm-code", totp(secret));
  await page.getByRole("button", { name: "Ativar 2FA" }).click();
  await page.locator(".codes span").first().waitFor();
  ok("2FA ativado com códigos de recuperação exibidos uma vez", (await page.locator(".codes span").count()) === 10);
  await page.getByRole("button", { name: "Sair" }).click();
  await page.waitForURL("**/login");
  await page.fill("#email", email); await page.fill("#password", password);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.fill("#code", totp(secret));
  await page.getByRole("button", { name: "Confirmar código" }).click();
  await page.waitForURL("**/feed");
  ok("login em duas etapas com TOTP", true);
  await page.getByRole("link", { name: "Fila de moderação" }).click();
  await page.waitForURL("**/moderacao");
  // localiza pelas pessoas desta execução (o banco local pode ter sobras de execuções anteriores)
  const carlaCard = page.locator("article", { has: page.locator(`a[href="/perfil/${carlaId}"]`) });
  const brunoRow = page.locator(".item", { has: page.locator(`a[href="/perfil/${brunoId}"]`) });
  await carlaCard.waitFor();
  await brunoRow.waitFor();
  await shot("15-moderacao");
  await carlaCard.getByRole("button", { name: "Recusar" }).click();
  await page.getByText("Explique a recusa em pelo menos 10 caracteres").first().waitFor();
  ok("recusa sem motivo é barrada com 422 no campo", true);
  await carlaCard.locator("textarea").fill("Carteira do CRM conferida por videochamada.");
  await carlaCard.getByRole("button", { name: "Aprovar selo" }).click();
  await page.getByText("Selo de Carla Nova aprovado.").waitFor();
  await brunoRow.getByRole("button", { name: "Aprovar entrada" }).click();
  await page.getByText("Bruno Tavares entrou em Dermatologia.").waitFor();
  ok("moderadora aprova selo e entrada em grupo", true);
  await carla.goto(BASE + "/perfil");
  await carla.getByText("CRM verificado na fonte oficial").waitFor();
  await bruno.goto(BASE + "/grupos");
  ok("Carla verificada e Bruno membro de Dermatologia", /Dermatologia[\s\S]*Você é membro/.test(await bruno.locator(".card", { hasText: "Dermatologia" }).innerText()));
  await brunoCtx.close(); await carlaCtx.close();

  ok("sem erros de JS nem console.error", errors.length === 0, errors.join(" | ").slice(0, 1500));
  console.log(steps.join("\n"));
  await browser.close();
})().catch(async (e) => {
  console.log(steps.join("\n"));
  for (const err of errors) console.error("console.error:", err);
  console.error(e.message.slice(0, 200));
  process.exit(1);
});
