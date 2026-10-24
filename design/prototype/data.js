/* Dados sintéticos do protótipo. Nenhuma pessoa real. Nomes de pacientes nunca aparecem: o feed é anonimizado. */
window.DE_DATA = (() => {
  const especialidades = [
    { code: "CARDIO", nome: "Cardiologia" },
    { code: "UTI", nome: "Medicina Intensiva" },
    { code: "DERM", nome: "Dermatologia" },
    { code: "PED", nome: "Pediatria" },
    { code: "ORTO", nome: "Ortopedia" },
    { code: "NEURO", nome: "Neurologia" },
    { code: "CLIN", nome: "Clínica Médica" },
  ];

  /* Classificações do feed. Cada publicação tem exatamente uma de cada eixo. */
  const classificacoes = {
    tipo: [
      { code: "caso", nome: "Caso clínico", icon: "nav/casos-clinicos" },
      { code: "parecer", nome: "Pedido de parecer", icon: "topbar/ajuda" },
      { code: "protocolo", nome: "Protocolo institucional", icon: "nav/instituicoes" },
      { code: "comunicado", nome: "Comunicado", icon: "topbar/notificacoes" },
      { code: "educacao", nome: "Educação continuada", icon: "nav/prontuario" },
    ],
    estado: [
      { code: "sem_resposta", nome: "Sem resposta" },
      { code: "em_discussao", nome: "Em discussão" },
      { code: "resolvido", nome: "Resolvido" },
    ],
    origem: [
      { code: "rede", nome: "Rede verificada" },
      { code: "instituicao", nome: "Minha instituição" },
      { code: "grupo", nome: "Meus grupos" },
    ],
  };

  const instituicoes = [
    { id: "i1", nome: "Hospital Santa Clara", tipo: "HOSPITAL", cidade: "Curitiba/PR", cnes: true, medicos: 212 },
    { id: "i2", nome: "Rede Vida Sul", tipo: "NETWORK", cidade: "Porto Alegre/RS", cnes: true, medicos: 980 },
    { id: "i3", nome: "UTI Adulto · Santa Clara", tipo: "UNIT", cidade: "Curitiba/PR", cnes: true, medicos: 38, pai: "i1" },
  ];

  const eu = {
    nome: "Dra. Ana Ribeiro", crm: "CRM-PR 123456", selo: "VERIFIED", rqe: ["UTI", "CLIN"], instituicao: "i1", mfa: true,
  };

  const autores = {
    a1: { nome: "Dra. Ana Ribeiro", crm: "CRM-PR 123456", selo: "VERIFIED", esp: "UTI", ini: "AR" },
    a2: { nome: "Dr. Bruno Tavares", crm: "CRM-SP 98765", selo: "VERIFIED", esp: "CARDIO", ini: "BT" },
    a3: { nome: "Dra. Carla Nunes", crm: "CRM-RS 45678", selo: "VERIFIED", esp: "DERM", ini: "CN" },
    a4: { nome: "Dr. Diego Souza", crm: "CRM-MG 22334", selo: "PENDING", esp: "PED", ini: "DS" },
    a5: { nome: "Hospital Santa Clara", crm: "CNES 1234567", selo: "VERIFIED", esp: "CLIN", ini: "SC", inst: true },
    a6: { nome: "Dra. Elisa Prado", crm: "CRM-PR 77889", selo: "VERIFIED", esp: "NEURO", ini: "EP" },
  };

  const posts = [
    { id: "p1", tipo: "caso", esp: "UTI", estado: "sem_resposta", origem: "grupo", autor: "a1", tempo: "há 20 min", pareceres: 0,
      titulo: "Choque séptico refratário, 60 anos, sem foco definido",
      resumo: "Vasopressor em dose alta há 36 h, lactato em queda lenta, culturas negativas até agora. Alguém usaria corticoide neste ponto?",
      midia: true, tags: ["sepse", "vasopressor"] },
    { id: "p2", tipo: "parecer", esp: "CARDIO", estado: "em_discussao", origem: "rede", autor: "a2", tempo: "há 1 h", pareceres: 3,
      titulo: "Parecer: ECG com supra difuso em paciente jovem",
      resumo: "Dor torácica pleurítica, troponina discretamente elevada. Pericardite × IAM? Peço opinião sobre conduta antes de cateterismo.",
      midia: true, tags: ["ECG", "pericardite"] },
    { id: "p3", tipo: "protocolo", esp: "CLIN", estado: "resolvido", origem: "instituicao", autor: "a5", tempo: "há 3 h", pareceres: 0,
      titulo: "Protocolo de profilaxia de TEV · v3 (vigente)",
      resumo: "Atualização aprovada pela Comissão. Mudanças: escore de Pádua obrigatório na admissão e revisão em 72 h.",
      midia: false, tags: ["TEV", "protocolo"] },
    { id: "p4", tipo: "caso", esp: "DERM", estado: "em_discussao", origem: "rede", autor: "a3", tempo: "há 5 h", pareceres: 5,
      titulo: "Lesão anular com bordas elevadas em antebraço",
      resumo: "Evolução de 3 semanas, prurido leve, sem resposta a antifúngico tópico. Foto anonimizada (sem rosto, sem metadados).",
      midia: true, tags: ["dermatose", "anular"] },
    { id: "p5", tipo: "comunicado", esp: "CLIN", estado: "resolvido", origem: "instituicao", autor: "a5", tempo: "ontem", pareceres: 0,
      titulo: "Manutenção do PACS neste sábado, 02h–05h",
      resumo: "Laudos de imagem ficarão indisponíveis na janela. Contingência: laudo verbal pelo plantão de radiologia.",
      midia: false, tags: ["TI", "PACS"] },
    { id: "p6", tipo: "educacao", esp: "NEURO", estado: "em_discussao", origem: "grupo", autor: "a6", tempo: "ontem", pareceres: 2,
      titulo: "Caso da semana: AVC de circulação posterior com NIHSS baixo",
      resumo: "Discussão guiada com 3 perguntas. Respostas aceitas até sexta; gabarito comentado depois.",
      midia: false, tags: ["AVC", "quiz"] },
    { id: "p7", tipo: "caso", esp: "PED", estado: "sem_resposta", origem: "rede", autor: "a4", tempo: "ontem", pareceres: 0,
      titulo: "Lactente com febre persistente e exantema",
      resumo: "Publicação bloqueada: autor com selo em análise. Aparece só para o próprio autor como rascunho.",
      midia: false, tags: ["febre"], bloqueado: true },
    { id: "p8", tipo: "parecer", esp: "UTI", estado: "resolvido", origem: "instituicao", autor: "a1", tempo: "há 2 dias", pareceres: 4,
      titulo: "Parecer resolvido: desmame ventilatório em DPOC",
      resumo: "Conduta adotada: teste de respiração espontânea com PSV 7, extubação para VNI. Nota SOAP registrada no PEP.",
      midia: false, tags: ["VM", "DPOC"], pep: true },
  ];

  const atores = [
    { id: "visitante", nome: "Visitante", icon: "topbar/perfil", entrada: "sem sessão", verificacao: "—", selo: null, mfa: "—",
      pode: ["Página institucional", "Diretório público de hospitais"], naoPode: ["Ver perfis completos", "Ler o feed", "Conversar"] },
    { id: "pendente", nome: "Profissional pendente", icon: "topbar/selo-pendente", entrada: "cadastro", verificacao: "e-mail (código de 6 dígitos)", selo: "PENDING", mfa: "opcional",
      pode: ["Completar perfil", "Enviar CRM/RQE", "Gerenciar privacidade"], naoPode: ["Publicar", "Comentar", "Conversar", "Prescrever"] },
    { id: "medico", nome: "Médico verificado", icon: "topbar/selo-verificado", entrada: "cadastro + selo", verificacao: "CRM ativo + RQE no CFM", selo: "VERIFIED", mfa: "recomendado",
      pode: ["Feed e grupos", "Publicar caso anonimizado", "Chat e vídeo E2EE", "Nota SOAP no PEP", "Receita e exame ICP-Brasil"], naoPode: ["Gerir instituição", "Moderar"] },
    { id: "gestor", nome: "Gestor hospitalar", icon: "nav/instituicoes", entrada: "convite da instituição", verificacao: "e-mail institucional + CNPJ e CNES", selo: "VERIFIED", mfa: "obrigatório",
      pode: ["Corpo clínico", "Papéis (RBAC) na sua árvore", "Protocolos e comunicados", "Dashboard agregado"], naoPode: ["Ver dado de paciente", "Ver atividade individual"] },
    { id: "diretor", nome: "Diretor-Técnico", icon: "nav/prontuario", entrada: "designação formal", verificacao: "CRM + ato de designação", selo: "VERIFIED", mfa: "obrigatório",
      pode: ["Aprovar regras clínicas", "Responder ao CFM", "Ver relatórios de qualidade"], naoPode: ["Acessar PEP de terceiros sem justificativa"] },
    { id: "moderador", nome: "Moderador / Admin", icon: "nav/administracao", entrada: "convite interno", verificacao: "—", selo: null, mfa: "obrigatório",
      pode: ["Conferir selos manualmente", "Moderar conteúdo", "Ver auditoria"], naoPode: ["Ler chat (E2EE)", "Prescrever"] },
    { id: "dpo", nome: "Encarregado (DPO)", icon: "nav/privacidade", entrada: "papel interno", verificacao: "—", selo: null, mfa: "obrigatório",
      pode: ["Atender pedidos do titular", "Registrar incidentes", "Ver registro de operações"], naoPode: ["Moderar", "Prescrever"] },
    { id: "paciente", nome: "Paciente (titular)", icon: "topbar/e2ee", entrada: "sem conta no MVP", verificacao: "link único da receita", selo: null, mfa: "—",
      pode: ["Receber receita por link", "Pedir direitos LGPD ao Encarregado"], naoPode: ["Entrar na rede", "Ver discussões"] },
  ];

  const consentimentos = [
    { id: "LOCATION", nome: "Localização", desc: "Usar sua localização para ordenar instituições por distância.", base: "Art. 7º, I" },
    { id: "REMINDERS", nome: "Lembretes", desc: "Avisar sobre casos sem resposta e prazos de parecer.", base: "Art. 7º, I" },
    { id: "CONTENT_RECOMMENDATION", nome: "Recomendação de conteúdo", desc: "Sugerir casos e grupos a partir da sua especialidade e atividade.", base: "Art. 11, I" },
    { id: "INSTITUTIONAL_COMMUNICATION", nome: "Comunicação institucional", desc: "Receber comunicados de instituições às quais você está vinculado por e-mail.", base: "Art. 7º, I" },
  ];

  const sessoes = [
    { id: "s1", aparelho: "Este aparelho · Chrome em Windows", local: "Curitiba/PR", quando: "agora", atual: true },
    { id: "s2", aparelho: "iPhone · app Doctor Explorer", local: "Curitiba/PR", quando: "há 2 h", atual: false },
    { id: "s3", aparelho: "Firefox em Linux", local: "São Paulo/SP", quando: "há 6 dias", atual: false },
  ];

  return { especialidades, classificacoes, instituicoes, eu, autores, posts, atores, consentimentos, sessoes };
})();
