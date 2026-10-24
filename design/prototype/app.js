/* Protótipo navegável · HTML/CSS/JS puro. Rotas por hash. Nenhuma chamada de rede. */
(() => {
  const D = window.DE_DATA;
  const $ = (s, r = document) => r.querySelector(s);
  const h = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const icon = (p, cls = "") => `<img class="${cls}" src="../icons/${p}.svg" alt="">`;
  const esp = (code) => D.especialidades.find((e) => e.code === code)?.nome ?? code;

  const SEAL = {
    VERIFIED: { icon: "topbar/selo-verificado", label: "CRM verificado" },
    PENDING: { icon: "topbar/selo-pendente", label: "Em análise" },
    REJECTED: { icon: "topbar/selo-rejeitado", label: "Não verificado" },
  };
  const seal = (s) => (s ? `<span class="seal ${s}">${icon(SEAL[s].icon)}${SEAL[s].label}</span>` : "");

  /* estado do feed: um valor por eixo de classificação; persistido só como conveniência deste navegador */
  const state = {
    feed: { tipo: "todos", esp: new Set(["todas"]), estado: "todos", origem: "todas", semResposta: false },
    consent: Object.fromEntries(D.consentimentos.map((c) => [c.id, false])),
    sessoes: D.sessoes.map((s) => ({ ...s })),
  };
  try {
    const saved = JSON.parse(localStorage.getItem("de-proto-feed") || "null");
    if (saved) Object.assign(state.feed, saved, { esp: new Set(saved.esp) });
  } catch (_) {}
  const persist = () => { try { localStorage.setItem("de-proto-feed", JSON.stringify({ ...state.feed, esp: [...state.feed.esp] })); } catch (_) {} };

  /* ---------- Feed ---------- */
  function filtrar() {
    const f = state.feed;
    return D.posts.filter((p) => {
      if (p.bloqueado && p.autor !== "a1") return false; // rascunho de outro autor nunca aparece
      if (f.tipo !== "todos" && p.tipo !== f.tipo) return false;
      if (!f.esp.has("todas") && !f.esp.has(p.esp)) return false;
      if (f.estado !== "todos" && p.estado !== f.estado) return false;
      if (f.origem !== "todas" && p.origem !== f.origem) return false;
      if (f.semResposta && p.estado !== "sem_resposta") return false;
      return true;
    });
  }

  function viewFeed() {
    const f = state.feed;
    const counts = Object.fromEntries(D.classificacoes.tipo.map((t) => [t.code, D.posts.filter((p) => p.tipo === t.code && !p.bloqueado).length]));
    const posts = filtrar();
    const minhas = new Set(D.eu.rqe);
    const espChips = [{ code: "todas", nome: "Todas" }, ...D.especialidades]
      .map((e) => `<button class="chip" type="button" data-esp="${e.code}" aria-pressed="${f.esp.has(e.code)}">${minhas.has(e.code) ? '<span class="dot" aria-hidden="true"></span>' : ""}${h(e.nome)}${minhas.has(e.code) ? '<span class="sr-only"> (sua especialidade)</span>' : ""}</button>`)
      .join("");
    const tabs = [{ code: "todos", nome: "Tudo", icon: "nav/inicio" }, ...D.classificacoes.tipo]
      .map((t) => `<button role="tab" type="button" id="tab-${t.code}" aria-selected="${f.tipo === t.code}" aria-controls="feed-list" data-tipo="${t.code}">${icon(t.icon)}${h(t.nome)}<span class="count">${t.code === "todos" ? D.posts.filter((p) => !p.bloqueado).length : counts[t.code]}</span></button>`)
      .join("");
    const origem = [{ code: "todas", nome: "Toda a rede" }, ...D.classificacoes.origem]
      .map((o) => `<button class="chip" type="button" data-origem="${o.code}" aria-pressed="${f.origem === o.code}">${h(o.nome)}</button>`).join("");
    const estado = [{ code: "todos", nome: "Qualquer estado" }, ...D.classificacoes.estado]
      .map((e) => `<button class="chip" type="button" data-estado="${e.code}" aria-pressed="${f.estado === e.code}">${h(e.nome)}</button>`).join("");

    return `
      <h1>Início</h1>
      <p class="lead">Feed da rede verificada, segmentado por tipo de publicação, especialidade, origem e estado. Ordem: mais recente primeiro, sem algoritmo de engajamento.</p>
      <section class="segments" aria-label="Segmentação do feed">
        <div class="tabs" role="tablist" aria-label="Tipo de publicação">${tabs}</div>
        <div class="chips" role="group" aria-label="Especialidade"><span class="label">Especialidade</span>${espChips}</div>
        <div class="row">
          <div class="chips" role="group" aria-label="Origem"><span class="label">Origem</span>${origem}</div>
          <label class="switch"><input type="checkbox" id="sw-sem" ${f.semResposta ? "checked" : ""}> Só sem resposta</label>
        </div>
        <div class="chips" role="group" aria-label="Estado"><span class="label">Estado</span>${estado}</div>
      </section>
      <p class="result-line" aria-live="polite">${posts.length} publicaç${posts.length === 1 ? "ão" : "ões"}</p>
      <div class="feed" id="feed-list" role="tabpanel" aria-labelledby="tab-${f.tipo}">
        ${posts.length ? posts.map(postCard).join("") : '<div class="card empty">Nenhuma publicação com esses filtros. Limpe um filtro para ver mais.</div>'}
      </div>`;
  }

  function postCard(p) {
    const a = D.autores[p.autor];
    const tipo = D.classificacoes.tipo.find((t) => t.code === p.tipo);
    const est = D.classificacoes.estado.find((e) => e.code === p.estado);
    const estIcon = { sem_resposta: "topbar/ajuda", em_discussao: "nav/grupos", resolvido: "topbar/selo-verificado" }[p.estado];
    return `
      <article class="card post ${p.bloqueado ? "blocked" : ""}" aria-labelledby="t-${p.id}">
        <div class="avatar ${a.inst ? "inst" : ""}" aria-hidden="true">${a.ini}</div>
        <div>
          <div class="head">
            <strong>${h(a.nome)}</strong><span>${h(a.crm)}</span>${seal(a.selo)}<span>·</span><span>${h(esp(p.esp))}</span><span>·</span><time>${h(p.tempo)}</time>
          </div>
          <div class="kind">${icon(tipo.icon)}${h(tipo.nome)}</div>
          <h3 id="t-${p.id}"><a href="#/caso/${p.id}" style="color:inherit;text-decoration:none">${h(p.titulo)}</a></h3>
          ${p.bloqueado ? `<div class="notice">${icon("topbar/selo-pendente")}<span>Publicação retida: o selo do autor está em análise. Só o autor vê este rascunho.</span></div>` : ""}
          <p>${h(p.resumo)}</p>
          ${p.midia ? `<div class="media"><span class="tag">${icon("nav/privacidade")}Mídia anonimizada</span>imagem do caso (sem rosto, sem EXIF/GPS)</div>` : ""}
          <div class="foot">
            <span class="status ${p.estado}">${icon(estIcon)}${h(est.nome)}</span>
            <span>${p.pareceres} parecer${p.pareceres === 1 ? "" : "es"}</span>
            ${p.pep ? `<span class="seal VERIFIED">${icon("nav/prontuario")}Registrado no PEP</span>` : ""}
            <span class="spacer"></span>
            <div class="tags">${p.tags.map((t) => `<span>#${h(t)}</span>`).join("")}</div>
          </div>
        </div>
      </article>`;
  }

  function bindFeed(root) {
    root.querySelectorAll("[data-tipo]").forEach((b) => b.addEventListener("click", () => { state.feed.tipo = b.dataset.tipo; persist(); render(); }));
    root.querySelectorAll("[data-esp]").forEach((b) => b.addEventListener("click", () => {
      const c = b.dataset.esp; const s = state.feed.esp;
      if (c === "todas") { s.clear(); s.add("todas"); } else { s.delete("todas"); s.has(c) ? s.delete(c) : s.add(c); if (!s.size) s.add("todas"); }
      persist(); render();
    }));
    root.querySelectorAll("[data-origem]").forEach((b) => b.addEventListener("click", () => { state.feed.origem = b.dataset.origem; persist(); render(); }));
    root.querySelectorAll("[data-estado]").forEach((b) => b.addEventListener("click", () => { state.feed.estado = b.dataset.estado; persist(); render(); }));
    $("#sw-sem", root)?.addEventListener("change", (e) => { state.feed.semResposta = e.target.checked; persist(); render(); });
  }

  /* ---------- Caso (detalhe) ---------- */
  function viewCaso(id) {
    const p = D.posts.find((x) => x.id === id);
    if (!p) return `<h1>Caso não encontrado</h1><p class="lead">Ele pode ter sido removido ou você não tem acesso. <a href="#/">Voltar ao início</a></p>`;
    const a = D.autores[p.autor];
    return `
      <h1>Caso clínico</h1>
      <a href="#/" class="btn" style="margin-bottom:12px">${icon("topbar/sair")}Voltar ao feed</a>
      ${postCard(p)}
      <h2>Pareceres</h2>
      <div class="feed">
        ${p.pareceres ? `<article class="card post"><div class="avatar" aria-hidden="true">BT</div><div><div class="head"><strong>Dr. Bruno Tavares</strong><span>CRM-SP 98765</span>${seal("VERIFIED")}<span>·</span><time>há 40 min</time></div><div class="kind">${icon("topbar/ajuda")}Parecer</div><p>Concordo com a suspeita. Sugiro ecocardiograma à beira do leito antes de qualquer decisão invasiva e repetir troponina em 6 h.</p></div></article>` : '<div class="card empty">Ainda sem parecer. Seja o primeiro a responder.</div>'}
      </div>
      <div class="toolbar">
        <button class="btn primary" type="button">${icon("topbar/ajuda")}Dar parecer</button>
        ${p.autor === "a1" ? `<button class="btn" type="button" id="btn-pep">${icon("nav/prontuario")}Registrar no prontuário</button>` : ""}
        <button class="btn" type="button">${icon("topbar/notificacoes")}Denunciar</button>
      </div>
      <p class="result-line">"Registrar no prontuário" aparece só para o autor do caso e abre a nota SOAP na clinical-api com o id opaco deste post (RF08).</p>`;
  }

  /* ---------- Atores ---------- */
  function viewAtores() {
    return `
      <h1>Atores do sistema</h1>
      <p class="lead">Um card por tipo de usuário: como entra, como é verificado, o que pode e o que não pode. Fonte normativa: <code>docs/TAXONOMIA_ATORES_E_CARDS.md</code>.</p>
      <div class="grid">
        ${D.atores.map((a) => `
          <article class="card actor" aria-labelledby="actor-${a.id}">
            <h3 id="actor-${a.id}">${icon(a.icon)}${h(a.nome)}</h3>
            ${a.selo ? seal(a.selo) : '<span class="seal" style="color:var(--text-3)">sem selo</span>'}
            <dl>
              <dt>Entrada</dt><dd>${h(a.entrada)}</dd>
              <dt>Verificação</dt><dd>${h(a.verificacao)}</dd>
              <dt>2FA</dt><dd>${h(a.mfa)}</dd>
            </dl>
            <ul aria-label="Pode">${a.pode.map((x) => `<li>${icon("topbar/selo-verificado")}${h(x)}</li>`).join("")}</ul>
            <ul aria-label="Não pode">${a.naoPode.map((x) => `<li class="no">${icon("topbar/selo-rejeitado")}${h(x)}</li>`).join("")}</ul>
          </article>`).join("")}
      </div>`;
  }

  /* ---------- Perfil ---------- */
  function viewPerfil() {
    const inst = D.instituicoes.find((i) => i.id === D.eu.instituicao);
    return `
      <h1>Perfil</h1>
      <article class="card post">
        <div class="avatar" aria-hidden="true">AR</div>
        <div>
          <div class="head"><strong>${h(D.eu.nome)}</strong><span>${h(D.eu.crm)}</span>${seal(D.eu.selo)}</div>
          <p>Especialidades com RQE: ${D.eu.rqe.map(esp).join(", ")}</p>
          <p>Instituição: ${h(inst.nome)} · ${h(inst.cidade)} <span class="seal VERIFIED">${icon("nav/instituicoes")}CNPJ e CNES conferidos</span></p>
          <p class="result-line">Visibilidade: <strong>rede verificada</strong> (padrão). Mudar nome, CRM ou especialidade devolve o selo para "Em análise".</p>
          <div class="toolbar"><button class="btn" type="button">Editar perfil</button><a class="btn" href="#/privacidade">${icon("nav/privacidade")}Privacidade</a></div>
        </div>
      </article>`;
  }

  /* ---------- Privacidade (LGPD) ---------- */
  function viewPrivacidade() {
    return `
      <h1>Privacidade e seus dados</h1>
      <p class="lead">Tudo desligado por padrão. Você liga, desliga e revoga pelo mesmo controle; cada mudança fica registrada com data e versão do texto.</p>
      <section class="card" aria-labelledby="h-cons">
        <h2 id="h-cons" style="margin-top:0">Consentimentos</h2>
        <div class="list">
          ${D.consentimentos.map((c) => `
            <div class="item">
              <div class="text"><label for="c-${c.id}"><strong>${h(c.nome)}</strong></label><small>${h(c.desc)}</small></div>
              <span class="base">LGPD ${h(c.base)}</span>
              <label class="switch"><input type="checkbox" id="c-${c.id}" data-consent="${c.id}" ${state.consent[c.id] ? "checked" : ""}><span class="sr-only">${h(c.nome)}</span></label>
            </div>`).join("")}
        </div>
      </section>
      <section class="card" aria-labelledby="h-dados" style="margin-top:16px">
        <h2 id="h-dados" style="margin-top:0">Seus dados</h2>
        <p>Veja o que guardamos, corrija o cadastro, exporte uma cópia (JSON versionado + ZIP cifrado, pronto em até 24 h) ou pergunte com quem compartilhamos.</p>
        <div class="toolbar">
          <button class="btn" type="button">Ver o que guardamos</button>
          <button class="btn" type="button">Corrigir cadastro</button>
          <button class="btn" type="button" id="btn-export">Exportar meus dados</button>
          <button class="btn" type="button">Compartilhamento</button>
        </div>
        <div class="status-steps" id="export-steps" hidden>
          <span class="done">${icon("topbar/selo-verificado")}Pedido recebido</span><span aria-current="step">${icon("topbar/selo-pendente")}Gerando (até 24 h)</span><span>Pronto para baixar</span>
        </div>
      </section>
      <section class="card" aria-labelledby="h-sess" style="margin-top:16px">
        <h2 id="h-sess" style="margin-top:0">Sessões e segurança</h2>
        <div class="list" id="sess-list">
          ${state.sessoes.map((s) => `
            <div class="item">
              <div class="text"><strong>${h(s.aparelho)}</strong><small>${h(s.local)} · ${h(s.quando)}${s.atual ? " · sessão atual" : ""}</small></div>
              ${s.atual ? `<span class="seal VERIFIED">${icon("topbar/e2ee")}2FA ativo</span>` : `<button class="btn" type="button" data-end="${s.id}">Encerrar</button>`}
            </div>`).join("")}
        </div>
        <div class="toolbar"><button class="btn" type="button" id="btn-end-all">Encerrar todas as outras</button><button class="btn" type="button">Códigos de recuperação</button></div>
      </section>
      <section class="card" aria-labelledby="h-del" style="margin-top:16px">
        <h2 id="h-del" style="margin-top:0">Excluir conta</h2>
        <p>Você tem 30 dias para reativar. Perfil, publicações e mensagens são eliminados; registros clínicos (notas, receitas) ficam guardados por 20 anos por obrigação legal.</p>
        <div class="toolbar"><button class="btn danger" type="button" id="btn-del">Excluir minha conta</button><a class="btn" href="#/encarregado">Falar com o Encarregado</a></div>
      </section>
      <dialog id="dlg-del" aria-labelledby="dlg-title" aria-describedby="dlg-desc">
        <h2 id="dlg-title">Excluir sua conta?</h2>
        <div id="dlg-desc">
          <p>O que acontece:</p>
          <ul><li>Carência até <strong>23/11/2026</strong>: entre de novo para reativar.</li><li>Depois disso, perfil, casos, pareceres e mensagens são eliminados e a chave dos seus dados é destruída.</li><li>Notas SOAP, receitas e pedidos de exame ficam no prontuário por 20 anos (Lei 13.787/2018).</li></ul>
          <div class="toolbar"><button class="btn" type="button" id="btn-copy">Baixar uma cópia antes</button></div>
          <label class="check"><input type="checkbox" id="ck-entendo"> <span>Entendo as consequências e quero excluir a conta.</span></label>
          <div class="field"><label for="pw">Senha</label><input type="password" id="pw" autocomplete="current-password"></div>
          <div class="field"><label for="totp">Código do aplicativo autenticador</label><input type="text" id="totp" inputmode="numeric" autocomplete="one-time-code"></div>
        </div>
        <div class="toolbar"><button class="btn" type="button" id="dlg-cancel">Cancelar</button><button class="btn danger" type="button" id="dlg-confirm" disabled>Excluir conta</button></div>
      </dialog>`;
  }

  function bindPrivacidade(root) {
    root.querySelectorAll("[data-consent]").forEach((i) => i.addEventListener("change", (e) => { state.consent[i.dataset.consent] = e.target.checked; toast(`${e.target.checked ? "Consentimento registrado" : "Consentimento revogado"} · ${new Date().toLocaleString("pt-BR")}`); }));
    $("#btn-export", root)?.addEventListener("click", () => { $("#export-steps", root).hidden = false; });
    root.querySelectorAll("[data-end]").forEach((b) => b.addEventListener("click", () => { state.sessoes = state.sessoes.filter((s) => s.id !== b.dataset.end); render(); toast("Sessão encerrada", () => { state.sessoes = D.sessoes.map((s) => ({ ...s })); render(); }); }));
    $("#btn-end-all", root)?.addEventListener("click", () => { state.sessoes = state.sessoes.filter((s) => s.atual); render(); toast("Outras sessões encerradas"); });
    const dlg = $("#dlg-del", root);
    $("#btn-del", root)?.addEventListener("click", () => dlg.showModal());
    $("#dlg-cancel", root)?.addEventListener("click", () => dlg.close());
    $("#ck-entendo", root)?.addEventListener("change", (e) => { $("#dlg-confirm", root).disabled = !e.target.checked; });
    $("#dlg-confirm", root)?.addEventListener("click", () => { dlg.close(); toast("Conta em carência até 23/11/2026. Entre de novo para reativar."); });
  }

  function viewEncarregado() {
    return `
      <h1>Contato com o Encarregado (DPO)</h1>
      <p class="lead">Canal para exercer seus direitos (LGPD Art. 18) e relatar incidentes. Resposta em até 15 dias, com número de protocolo.</p>
      <section class="card">
        <div class="field"><label for="tipo">Assunto</label><input type="text" id="tipo" value="Pedido de informação sobre compartilhamento (Art. 18, VII)"></div>
        <div class="field"><label for="msg">Mensagem</label><input type="text" id="msg" placeholder="Descreva o pedido sem incluir dados de pacientes"></div>
        <div class="toolbar"><button class="btn primary" type="button" id="btn-dpo">Enviar ao Encarregado</button></div>
        <p class="result-line">Encarregado: {{NOME}} · {{e-mail do canal}} · Registro de operações disponível a pedido (Art. 37).</p>
      </section>`;
  }

  function viewSimples(titulo, texto) {
    return `<h1>${h(titulo)}</h1><p class="lead">${h(texto)}</p><div class="card empty">Tela fora do escopo deste protótipo. Veja o RF correspondente em <code>docs/rf/</code>.</div>`;
  }

  /* ---------- infra ---------- */
  let toastTimer;
  function toast(msg, undo) {
    $("#toast")?.remove();
    const el = document.createElement("div");
    el.className = "toast"; el.id = "toast"; el.setAttribute("role", "status");
    el.innerHTML = `<span>${h(msg)}</span>${undo ? '<button type="button">Desfazer</button>' : ""}`;
    if (undo) el.querySelector("button").addEventListener("click", () => { undo(); el.remove(); });
    document.body.appendChild(el);
    clearTimeout(toastTimer); toastTimer = setTimeout(() => el.remove(), undo ? 10000 : 4000);
  }

  const routes = {
    "": { title: "Início", view: viewFeed, bind: bindFeed, nav: "inicio" },
    "casos": { title: "Casos clínicos", view: () => { state.feed.tipo = "caso"; return viewFeed(); }, bind: bindFeed, nav: "casos" },
    "caso": { title: "Caso", view: (id) => viewCaso(id), nav: "casos" },
    "atores": { title: "Atores do sistema", view: viewAtores, nav: "atores" },
    "perfil": { title: "Perfil", view: viewPerfil, nav: "perfil" },
    "privacidade": { title: "Privacidade", view: viewPrivacidade, bind: bindPrivacidade, nav: "privacidade" },
    "encarregado": { title: "Encarregado", view: viewEncarregado, nav: "privacidade" },
    "grupos": { title: "Grupos", view: () => viewSimples("Grupos", "Grupos fechados por especialidade (RF04)."), nav: "grupos" },
    "mensagens": { title: "Mensagens", view: () => viewSimples("Mensagens", "Chat criptografado ponta a ponta (RF06)."), nav: "mensagens" },
    "instituicoes": { title: "Instituições", view: () => viewSimples("Instituições", "Diretório hospitalar com CNPJ e CNES conferidos (RF11)."), nav: "instituicoes" },
  };

  function render() {
    const [, path = "", arg] = location.hash.replace(/^#\/?/, "").match(/^([^/]*)\/?(.*)$/) || [];
    const r = routes[path] || { title: "Página não encontrada", view: () => viewSimples("Página não encontrada", "Confira o endereço.") };
    document.title = `${r.title} · Doctor Explorer`;
    const main = $("#main");
    main.innerHTML = r.view(arg);
    r.bind?.(main);
    document.querySelectorAll(".nav a").forEach((a) => { if (a.dataset.nav === r.nav) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current"); });
    $(".sidebar").classList.remove("open");
    main.focus({ preventScroll: true });
  }

  window.addEventListener("hashchange", render);
  document.addEventListener("DOMContentLoaded", () => {
    $("#btn-menu").addEventListener("click", () => $(".sidebar").classList.toggle("open"));
    $("#btn-theme").addEventListener("click", (e) => {
      const cur = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = cur; e.currentTarget.setAttribute("aria-pressed", cur === "dark");
    });
    $("#btn-plantao").addEventListener("click", (e) => { const on = e.currentTarget.getAttribute("aria-pressed") !== "true"; e.currentTarget.setAttribute("aria-pressed", on); toast(on ? "Modo plantão ligado: só notificações urgentes" : "Modo plantão desligado"); });
    document.addEventListener("keydown", (e) => { if (e.key === "/" && !/input|textarea/i.test(e.target.tagName)) { e.preventDefault(); $("#q").focus(); } });
    render();
  });
})();
