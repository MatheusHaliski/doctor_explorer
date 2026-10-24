# Doctor Explorer · Benchmark de concorrentes e análogos

| Campo | Valor |
|---|---|
| Versão | 0.1 (pesquisa de mesa) |
| Data da pesquisa | 2026-10-24 |
| Documento-base | [ESPECIFICACAO_TECNICA.md](../ESPECIFICACAO_TECNICA.md) |
| Método | Pesquisa web (sites oficiais, documentos regulatórios, relatórios a investidores, imprensa especializada, lojas de apps, Reclame Aqui, Trustpilot, Capterra, Reddit) |

> **Como ler este documento.** Muitos números (membros, médicos cadastrados, receita) são
> **autodeclarados** pelas próprias empresas e variam entre fontes e datas. Quando há divergência, ela é
> indicada. Itens marcados com **(não confirmado)** não foram verificados em fonte primária recente e
> precisam de checagem antes de entrar em material externo (pitch, site, contrato). Reclamações de usuários
> vêm de amostras pequenas e não representam o produto inteiro; servem para mapear riscos, não para medir
> satisfação.

---

## Sumário

1. [Resumo executivo](#1-resumo-executivo)
2. [Mapa do mercado](#2-mapa-do-mercado)
3. [Fichas: redes profissionais e comunidades de casos (internacional)](#3-redes-profissionais-e-comunidades-de-casos-internacional)
4. [Fichas: comunicação clínica segura (internacional)](#4-comunicação-clínica-segura-internacional)
5. [Fichas: Brasil](#5-brasil)
6. [(a) Tabela comparativa](#6-a-tabela-comparativa-funcionalidades--produtos)
7. [(b) Lacunas de mercado no Brasil](#7-b-lacunas-de-mercado-que-o-doctor-explorer-pode-ocupar-no-brasil)
8. [(c) Funcionalidades que viraram padrão](#8-c-funcionalidades-que-viraram-padrão)
9. [(d) Lições sobre adoção](#9-d-lições-sobre-adoção-e-efeitos-de-rede)
10. [(e) Tipos de usuário atendidos](#10-e-tipos-de-usuário-atendidos-por-esses-produtos)
11. [Implicações para a especificação](#11-implicações-para-a-especificação-do-doctor-explorer)
12. [Fontes](#12-fontes)

---

## 1. Resumo executivo

- **Ninguém no Brasil junta as quatro funções do Doctor Explorer.** A pesquisa não encontrou rede
  brasileira que combine verificação de CRM/RQE, discussão de casos anonimizados, comunicação segura entre
  pares e ato clínico formal (SOAP/PEP + prescrição ICP-Brasil). O mercado nacional está fatiado:
  conhecimento (Whitebook), PEP de consultório (iClinic, Amplimed), prescrição (Memed, plataforma do CFM),
  marketplace de pacientes (Doctoralia), telemedicina B2B (Conexa, Docway), laudo remoto (Portal
  Telemedicina) e sistema hospitalar (MV, Tasy). A conversa entre médicos acontece no **WhatsApp**.
- **Lá fora, o modelo vencedor é "utilidade gratuita + rede verificada + receita da indústria".**
  A Doximity tem mais de 3 milhões de membros, mais de 85% dos médicos dos EUA e receita de US$ 644,9 mi no
  ano fiscal de 2026. Ela cresceu com perfis pré-criados a partir do registro público (NPI), indicações
  entre colegas (80% dos novos usuários em 2013) e ferramentas de uso diário (discador com número
  mascarado, fax, telemedicina, scribe com IA). Figure 1, Sermo, Docquity e OpenEvidence também são
  gratuitos para o médico e vivem de pharma, pesquisa de mercado ou publicidade.
- **A comunicação clínica hospitalar segue outra lógica.** TigerConnect, Vocera (Stryker), Epic Secure Chat
  e Teams entram por contrato institucional, integração com EHR, plantão e alarmes. O efeito de rede fica
  dentro do hospital, não entre hospitais.
- **Os fracassos ensinam tanto quanto os sucessos.** O Hospify fechou em 2022 quando a demanda, puxada
  pela fiscalização de proteção de dados, sumiu. O QuantiaMD, financiado por pharma, acabou. O Pando perdeu
  o endosso de uma sociedade médica por salvar fotos no rolo da câmera. O Whitebook perde assinantes para
  LLMs gratuitos desde que aumentou o preço em 2024.
- **Padrões que o usuário já espera:** verificação em minutos, perfil pré-preenchido, chat e vídeo sem
  atrito, câmera dentro do app com anonimização, notificações sem conteúdo, diretório de colegas, prescrição
  digital gratuita enviada por WhatsApp/SMS/e-mail e, desde 2025, **IA gratuita** (scribe e referência
  clínica).
- **Oportunidade principal:** ser o **substituto conforme dos grupos de WhatsApp médicos**, com uma ponte
  que hoje não existe entre a discussão informal e o registro formal (parecer/SOAP no PEP, receita
  assinada). Entrar pelo hospital (corpo clínico verificado, RBAC, auditoria) e crescer pela especialidade.

---

## 2. Mapa do mercado

| Categoria | Produtos analisados | Quem paga | Relação com o Doctor Explorer |
|---|---|---|---|
| Rede profissional médica | Doximity, Sermo, Docquity, LinkedIn (genérico) | Indústria farmacêutica, recrutadores, pesquisa de mercado | Concorrente conceitual direto (nenhum opera no Brasil com verificação por CRM) |
| Comunidade de casos e imagens | Figure 1, MedShr, Medscape Consult | Indústria (conteúdo patrocinado), mídia | Concorre com o RF04/RF05 (grupos, feed, anonimização) |
| Mensageria clínica para profissional | Doctolib Siilo, Pando, Hospify (extinto), WhatsApp | Grátis para o usuário; instituições pagam recursos extras | Concorre com o RF06/RF07 (chat e vídeo) |
| Comunicação clínica hospitalar (CC&C) | TigerConnect, Vocera/Stryker, Epic Secure Chat, Microsoft Teams | Hospital (licença, hardware) | Concorre com o módulo hospitalar (RF11–RF13) em instituições grandes |
| Pré-hospitalar e emergência | Pulsara (TigerConnect/Twiage também) | Hospital paga, EMS usa grátis | Análogo para fluxos de transferência (fora do MVP) |
| IA clínica | OpenEvidence, Doximity GPT/Pathway, Whitebook Assist, iClinic Assist, Doctoralia Noa | Publicidade, assinatura | Define a expectativa de "IA grátis" (CDS está fora do MVP) |
| Brasil: conhecimento e prática | Whitebook, iClinic, Amplimed | Assinatura do médico | Base instalada de médicos, possível integração ou concorrência no PEP |
| Brasil: prescrição | Memed, Prescrição Eletrônica CFM, Ampli Prescreve | Indústria (Memed) ou ninguém (CFM) | Concorre com o RF09; define que prescrição é **gratuita** |
| Brasil: marketplace e telemedicina | Doctoralia, Conexa, Docway, Portal Telemedicina | Médico (Doctoralia), empresas e operadoras (Conexa, Docway), clínicas (Portal) | Adjacente; foco no paciente, fora do MVP |
| Brasil: sistema hospitalar | MV (SOUL MV), Tasy (Philips → Bionexo) | Hospital | Parceiro de integração obrigatório (PEP institucional) |

---

## 3. Redes profissionais e comunidades de casos (internacional)

### 3.1 Doximity (EUA)

| Aspecto | Achado |
|---|---|
| Status | Ativa, listada na NYSE (DOCS). Receita de US$ 644,9 mi no ano fiscal encerrado em 31/03/2026 (+13%). Mais de 3 mi de membros, mais de 85% dos médicos dos EUA. Recorde de 800 mil prescritores ativos nas ferramentas de fluxo no 4º trimestre. |
| Público | Médicos, NPs, PAs, farmacêuticos e estudantes **dos EUA**. Não opera no Brasil. |
| Funcionalidades | Perfil profissional, feed de notícias médicas, mensagens seguras, **Doximity Dialer** (voz e vídeo com o número do consultório no identificador de chamadas), fax digital, **Doximity Scribe** (IA gratuita lançada em jul/2025, integrada ao Dialer), **Doximity GPT** (cartas de pré-autorização e recursos a convênios), referência clínica após a compra da **Pathway** (jul/2025, US$ 26 mi em dinheiro + até US$ 37 mi em ações), vagas e recrutamento, escalas de plantão (Amion, adquirida em 2022 **(não confirmado nesta pesquisa)**). |
| Verificação | Perfil pré-criado a partir do NPI e do Medicare; o médico "reivindica" o perfil. Depois confirma e-mail de domínio médico e envia documento oficial. Perguntas de desafio por ferramenta de gestão de identidade e revisão manual de licença. Conta não verificada não usa os recursos. |
| Modelo de negócio | Gratuito para o profissional. A maior parte da receita vem de **marketing farmacêutico** e de soluções para sistemas de saúde; depois vêm recrutamento e assinaturas de ferramentas. Retenção líquida de receita de 112% (12 meses, 3º tri FY26). |
| Diferenciais | Cobertura quase total da profissão; utilidades diárias (o Dialer é a razão mais citada para abrir o app); IA gratuita; BAA da HIPAA para todos os usuários do Scribe. |
| Pontos fracos e reclamações | No Reddit, médicos dizem usar **só o Dialer**, reclamam de **excesso de e-mails** e da baixa qualidade dos comentários. Há desconfiança de que a empresa vende "engajamento" para pharma. Críticas de moderação de conteúdo sobre vacinas (CNBC, via Reclaim The Net). |
| Privacidade e compliance | Diz não vender e-mail nem celular; o Scribe não guarda áudio, segundo a empresa. A monetização depende de segmentação de anúncios para um público verificado, o que no Brasil esbarraria em LGPD (consentimento para marketing) e na Res. CFM 1.595/2000 (vínculo entre prescrição e vantagem da indústria). |
| Lição para o DE | O efeito de rede veio de **utilidade individual primeiro** (ligar para paciente sem expor o número), não do feed. |

### 3.2 Figure 1 (Canadá → EUA, global)

| Aspecto | Achado |
|---|---|
| Status | Ativo. Hoje é operado pela **PW Figure 1 LLC, "uma propriedade Formedics"**, grupo de mídia médica que também tem Physician's Weekly e OncWeekly. A Formedics comprou a AMC Media em ago/2024 e chegou a ~1,5 mi de profissionais verificados. Ganhou prêmio Global Health & Pharma em 2025. Podcast DDx na 12ª temporada. |
| Público | Profissionais de saúde em mais de 100 países (médicos, enfermeiros, estudantes). Chegou ao Brasil por volta de 2015, com 100 mil+ profissionais na época. |
| Funcionalidades | Feed de casos com imagens, comentários, discussão por especialidade, desafios de diagnóstico, podcast. O site diz que 40% dos clínicos que não resolviam um caso sozinhos chegaram a uma resolução pela colaboração na plataforma (dado da própria empresa). |
| Verificação | Para postar ou comentar é preciso provar vínculo profissional (documentos de licença). Historicamente, a visualização era aberta a mais gente. As imagens passam por revisão humana para retirar identificadores, e as que não atendem aos critérios são apagadas. |
| Modelo de negócio | Gratuito. Desde 2017, **conteúdo patrocinado "par a par"** e enquetes pagas pela indústria. O CEO disse à TechCrunch que médicos "odeiam spam de pharma", por isso o patrocínio vem em forma de médico ensinando médico. |
| Diferenciais | Pioneiro do "Instagram dos médicos"; 1 mi+ de profissionais e bilhões de visualizações de casos (2017); forte apelo educacional. |
| Pontos fracos e reclamações | Não foram encontradas reclamações sistemáticas recentes. Riscos conhecidos do formato: qualidade variável dos comentários, consentimento do paciente, presença de estudantes, monetização via indústria. A absorção por um grupo de mídia indica que a comunidade isolada não sustentou um negócio independente. |
| Privacidade e compliance | Desidentificação manual das imagens; diretrizes de comunidade e página "Safe and Secure". |
| Lição para o DE | O consumo de casos interessantes ("feed educativo") traz o usuário de volta mesmo sem ele postar. A anonimização com revisão humana já era padrão em 2014. |

### 3.3 Sermo (EUA, global)

| Aspecto | Achado |
|---|---|
| Status | Ativo. Diz ter 1 mi+ de profissionais verificados em 150 países (outras fontes citam 1,3 mi). Em 2025 abriu ao público o **Drug Ratings** (1,1–1,2 mi de avaliações de medicamentos feitas por médicos) e relançou o app. |
| Público | Médicos e profissionais de prática avançada (APPs). |
| Funcionalidades | Fórum entre pares, discussão de casos, avaliações de medicamentos, **pesquisas pagas**, newsroom com sondagens (ex.: uso de IA por médicos). |
| Verificação | "Tripla verificação" de licença e credenciais no cadastro; isso garante que só médicos em atividade recebam as pesquisas. |
| Modelo de negócio | Gratuito; o médico **recebe** por pesquisas. A receita vem de pesquisa de mercado e engajamento vendidos à indústria. |
| Diferenciais | Incentivo financeiro direto; espaço de desabafo e opinião franca entre pares (o Sermo é conhecido por permitir postagem anônima entre verificados **(não confirmado nesta pesquisa)**). |
| Pontos fracos e reclamações | Trustpilot: usuários que **não recebem pesquisas** ou são **eliminados na triagem** depois de responder perguntas; anúncios que prometem mais pesquisas do que há. Saques e suporte são bem avaliados. |
| Privacidade e compliance | Termos proíbem usar o Drug Ratings com ferramentas de IA; conteúdo é gerado pelo usuário, com isenção de responsabilidade. |
| Lição para o DE | Dinheiro atrai cadastro, mas não cria comunidade clínica. Se o DE tiver pesquisas pagas um dia, a triagem precisa ser transparente. |

### 3.4 Medscape Consult (WebMD/Internet Brands)

| Aspecto | Achado |
|---|---|
| Status | Lançado em nov/2015 como recurso do app Medscape. A ficha do app na loja ainda cita "Medscape Consult". Status atual **não confirmado**; não foi encontrado anúncio de descontinuação. |
| Público | Somente médicos registrados como médicos na conta Medscape. |
| Funcionalidades | Postar casos, fazer perguntas e responder, com moderação editorial. |
| Verificação | Cadastro Medscape com perfil de médico (rigor não documentado publicamente). |
| Modelo de negócio | Gratuito; o Medscape vive de publicidade e educação médica patrocinada. |
| Dados de uso (estudo Scripps/WebMD, npj Digital Medicine, nov/2015–out/2017) | 310.563 médicos acessaram; só **37.706 (12%) foram ativos**; desses, **90,3% só responderam**. 67,4% da atividade veio do celular; mediana de ~90 min até a primeira resposta; usuários em 171 países. Médicos mais jovens postam casos, os mais experientes respondem. |
| Lição para o DE | A regra 1-9-90 vale para casos clínicos: o produto precisa servir ao **leitor** e ao **respondedor**, não só a quem posta. Tempo até a primeira resposta é a métrica que importa. |

### 3.5 Outros relevantes (internacional)

| Produto | O que é | Verificação | Modelo | Por que importa |
|---|---|---|---|---|
| **MedShr** (Reino Unido) | App de discussão de casos com **termo de consentimento do paciente dentro do app** e ferramenta de anonimização de imagem; casos para a comunidade ou para um colega só. Diz ter 1,5 mi+ de usuários e conformidade HIPAA/GMC/GDPR (autodeclarado). | "100% verificados" (método não publicado) | Gratuito, com parcerias de educação | É o análogo mais próximo do RF04/RF05; o consentimento no app é recurso esperado. |
| **Docquity** (Singapura, Sudeste Asiático) | Rede de 410 mil+ médicos, CME com 250+ associações médicas, assistente de IA "Ask Dx". Série C de US$ 44 mi liderada pela Itochu. | Verificação de médicos (método não publicado) | Pharma, consumer health e medtech; insights de dados | Mostra que o modelo Doximity funciona em mercados emergentes com parceria de **sociedades médicas**. |
| **OpenEvidence** (EUA) | Busca clínica com IA. Valuation de US$ 12 bi (jan/2026). 430 mil+ médicos cadastrados em jul/2025 (40% dos EUA); números posteriores variam. | Médicos verificados por NPI | Gratuito; anúncios farmacêuticos (CPM de US$ 70–150+, fonte fraca) | Define a expectativa de IA clínica gratuita; tira o lugar de produtos pagos de referência (ver Whitebook). |
| **Pando** (Reino Unido, NHS) | Mensageria 1:1, equipes e fóruns, listas de tarefas, galeria de imagens dentro do app, diretório do hospital, status de indisponibilidade. Aprovado no DTAC da NHS England (segundo o fornecedor). | Só profissionais de saúde e assistência social verificados | Gratuito para o profissional; NHS/trusts | Incidente de fotos salvas no rolo da câmera fez a Primary Care Dermatology Society retirar o endosso; o problema foi corrigido com câmera própria. |
| **LinkedIn** | Rede profissional genérica, usada por médicos para carreira e marca pessoal. | Nenhuma verificação de registro profissional | Recrutamento, anúncios, assinaturas | Concorre pela atenção, mas não serve para caso clínico (sem sigilo, sem verificação). |

---

## 4. Comunicação clínica segura (internacional)

### 4.1 Doctolib Siilo (Holanda → Europa)

| Aspecto | Achado |
|---|---|
| Status | Fundado em 2015/2016 em Amsterdã ("WhatsApp dos médicos"). **Adquirido pela Doctolib** (França) em março de 2023, não por uma "MedTech"; rebatizado de *Doctolib Siilo*. 425–450 mil profissionais e mais de 1 bilhão de mensagens trocadas. |
| Público | Profissionais de saúde na Holanda, Bélgica, Alemanha, França e Reino Unido; organizações e associações. |
| Funcionalidades | Mensagens, fotos, vídeos e arquivos criptografados; **proteção por PIN** e **notificações ocultas**; chamadas de voz e vídeo; **"casos de paciente"**, que guardam conversas e mídia de um paciente separadas dos demais chats; redes fechadas para organizações e associações; app de desktop. |
| Verificação | Verificação de profissional de saúde (no NL, pelo registro BIG **(não confirmado nesta pesquisa)**). |
| Modelo de negócio | Gratuito para o profissional; recursos para instituições e, agora, integração com o ecossistema Doctolib (agenda, software de consultório). |
| Diferenciais | Experiência de WhatsApp com controles de sigilo; o "caso de paciente" é a coisa mais próxima de uma ponte entre conversa e prontuário. |
| Pontos fracos | Não grava em prontuário nem prescreve; depende do ecossistema Doctolib para virar negócio. |
| Lição para o DE | A mensageria gratuita sozinha não sustentou a empresa; ela foi comprada por um ecossistema de agenda e prontuário. O DE já nasce com o ato clínico. |

### 4.2 Hospify (Reino Unido) — extinto

| Aspecto | Achado |
|---|---|
| Status | **Fechou em 31/01/2022.** Fundado em 2014 por dois cirurgiões do NHS; em 2020 foi o primeiro app de mensageria clínica aprovado pelo NHS. |
| Por que fechou | A demanda vinha da exigência de conformidade com a lei de proteção de dados do Reino Unido. Quando o governo suspendeu trechos do Data Protection Act 2018 na pandemia, a "curva de crescimento exponencial colapsou". Somaram-se a falta de clientes e investimento e a incerteza pós-Brexit sobre a adequação de dados com a UE. Contas fechadas, dados removidos, assinaturas devolvidas. |
| Lição para o DE | **Conformidade não é proposta de valor suficiente.** Se a única razão para trocar o WhatsApp for "a lei manda", o produto fica refém da fiscalização. O DE precisa ser **melhor** que o WhatsApp para o trabalho clínico. |

### 4.3 TigerConnect (EUA)

| Aspecto | Achado |
|---|---|
| Status | Ativo; Best in KLAS 2024 em comunicação clínica (ambulatorial e pós-agudo, amostra de 40 clientes). Comprou a **Twiage** (nov/2024: comunicação pré-hospitalar e central de transferências) e a **eVideon** (ago/2025: quarto inteligente e engajamento do paciente). Não há aquisição pela Stryker/Vocera; são concorrentes. |
| Público | Hospitais e sistemas de saúde: médicos, enfermagem, plantonistas, central de transferências. |
| Funcionalidades | Mensageria por papel/função, paging, escalas de plantão, alarmes e alertas, chamadas, integração com EHR, fluxos de EMS. |
| Verificação | Provisionamento pela instituição (diretório corporativo), não verificação individual de licença. |
| Modelo de negócio | Licença institucional (SaaS). |
| Pontos fracos e reclamações | Capterra, Software Advice e App Store: **logouts frequentes e PIN extra**, **notificações que param** ou não somem, integrações "desajeitadas", busca e gestão de grupos difíceis, **falta de integração com Epic** em alguns clientes. Nota na App Store em torno de 2,9–3,1/5. Estudo na Tufts (JMIR 2025) mostrou carga de mensagens muito maior para internos que para enfermeiros, com picos na visita da manhã e de madrugada. |
| Lição para o DE | Notificação confiável e sessão que não expira a toda hora são requisitos de segurança do paciente, não "polimento". |

### 4.4 Vocera (Stryker)

| Aspecto | Achado |
|---|---|
| Status | Comprada pela **Stryker** em 2022 (≈US$ 3,09 bi). Linha atual: crachás *hands-free* (Smartbadge, Minibadge, **Sync Badge** com palavra de ativação "Okay Vocera", B7000) e **Vocera Edge** (app em nuvem para smartphone, SSO e *deep link* com os apps móveis da Epic; folheto de out/2025). |
| Público | Enfermagem e equipes de beira-leito, hospitais e instituições de longa permanência. |
| Funcionalidades | Voz *hands-free*, chamada por papel, integração de alarmes de equipamentos e campainha, mensagens. |
| Modelo de negócio | Hardware + software + serviços para a instituição. |
| Lição para o DE | Em hospital, quem decide é a TI e a enfermagem. A integração com alarmes e com a cama (Stryker) é um ativo que uma rede social não replica; o DE não deve competir nesse nível. |

### 4.5 Epic Secure Chat

| Aspecto | Achado |
|---|---|
| Status | Recurso nativo do EHR Epic (Hyperspace, Haiku, Canto e Rover). |
| Funcionalidades | Chat 1:1 e em grupo sobre um paciente, com acesso ao prontuário a partir da mensagem (o prontuário só pode ser anexado ao **iniciar** a conversa). Não permite fazer pedidos médicos pelo chat; não substitui o contato de plantão. |
| Verificação | Usuário do EHR da instituição. |
| Pontos fracos e reclamações | Pesquisa em Rochester (156 respondentes): 64,1% dos hospitalistas, 52,6% dos residentes e 64,7% dos APPs relataram **mensagens enviadas à pessoa errada**; o chat é menos usado para urgências. Em Vermont, a satisfação melhorou quando o chat foi somado ao paging, e chamadas não urgentes caíram. |
| Compliance | Na Northwell, o chat **não faz parte do prontuário** para fins de liberação de informação, mas é **descobrível** (*discoverable*) em litígio. |
| Lição para o DE | Separar com clareza o que é conversa (social, com retenção curta) e o que é registro (PEP, 20 anos) é decisão de produto e de risco jurídico. O DE já faz isso (§2.4 da especificação). Contexto do paciente e destinatário certo são as dores principais. |

### 4.6 Microsoft Teams na saúde

| Aspecto | Achado |
|---|---|
| Status | Usado por hospitais e pelo NHS. O app **Virtual Appointments** foi **descontinuado**; a Microsoft recomenda Bookings + Teams Premium (SMS, sala de espera com marca, fila, analytics). Bookings tem tipo de negócio "Saúde". |
| Funcionalidades | Chat, reuniões, anotação em imagens e notificações prioritárias (lançadas em 2018 como recursos para clínicos), integração com Epic para ver dados do paciente, **Dragon Copilot** (IA de documentação, desde mar/2025, a partir do DAX Copilot e do Dragon Medical One). |
| Verificação | Conta corporativa (Entra ID). Sem verificação de registro profissional. |
| Compliance | Possível operar sob HIPAA com configuração (BAA, retenção, E2EE opcional em chamadas 1:1), mas a responsabilidade de configurar é da TI. |
| Lição para o DE | Teams já está "dentro" de muitos hospitais e é gratuito no pacote Microsoft 365. O DE vence só onde Teams não chega: **entre instituições**, com **verificação de CRM** e **ato clínico**. |

### 4.7 Pulsara (EUA, pré-hospitalar)

| Aspecto | Achado |
|---|---|
| Status | Ativo; pacotes Pulsara ONE, UNITED e MED OPS. |
| Público | Agências de EMS/bombeiros e hospitais receptores; gestão de emergências regionais. |
| Funcionalidades | "Canal por paciente" que reúne toda a equipe envolvida; ativação de protocolos de AVC, IAM com supra, trauma e sepse; vídeo ao vivo, áudio, imagens e ECG; controle médico de destino; **integração com o desfibrilador ZOLL X Series (ECG de 12 derivações)**; API pública em formato NEMSIS para ePCR (ImageTrend); triagem de múltiplas vítimas com pulseiras. A empresa cita redução de ~30% no tempo de tratamento (dado próprio, 2018). |
| Verificação | Cadastro por organização (agência ou hospital). |
| Modelo de negócio | **Grátis para o EMS**; o hospital paga conforme o volume do pronto-socorro (sob cotação). |
| Pontos fracos | Não substitui o ePCR; a agência mantém outro sistema. Avaliações públicas escassas. |
| Lição para o DE | O modelo "lado que gera demanda usa grátis, lado que recebe valor paga" funciona em saúde. A comunicação **centrada no paciente** (e não na pessoa) reduz mensagens perdidas. |

---

## 5. Brasil

### 5.1 Contexto do mercado brasileiro

- **Tamanho:** a *Demografia Médica no Brasil 2025* (FMUSP/MS/AMB) projeta **635.706 médicos** no fim de 2025
  (2,98 por mil habitantes), com mulheres pela primeira vez como maioria (50,9%). 58% dos médicos atuam em 48
  municípios com mais de 500 mil habitantes; 4.895 cidades, com 31% da população, têm 8% dos médicos.
- **Verificação:** o CFM tem um **webservice oficial "Listagem de médicos"** (Res. CFM 2.309/2022) com
  nome, CRM, UF, situação da inscrição e especialidade registrada, atualizado diariamente. Custa **R$ 948 por
  ano** para empresas privadas (grátis para entes públicos), contratado via SEI-Medicina. Não devolve CPF,
  endereço nem contato. Há também a busca pública gratuita e o app CFM.
- **Prescrição digital:** o CFM oferece **certificado ICP-Brasil em nuvem gratuito** para médicos adimplentes
  e uma **plataforma gratuita de prescrição**. A Anvisa aprovou a **RDC 1.000/2025** (dez/2025), que permite
  receitas de controle especial (incluindo azul e amarela) em meio eletrônico, desde que emitidas por
  plataformas **integradas ao SNCR** via API. O prazo de operação foi adiado para 30/09/2026; a
  pesquisa não confirmou se o SNCR já está em funcionamento **(verificar no site da Anvisa)**.
- **WhatsApp:** 65% dos médicos falavam com pacientes por aplicativo de mensagem fora do horário de
  atendimento (APM, fev/2020, 2,2 mil médicos). Não há pesquisa nacional recente que meça o uso para
  discutir casos entre colegas, mas a prática é ampla (ver §5.12).

### 5.2 Whitebook (PEBMED/Afya)

| Aspecto | Achado |
|---|---|
| Status | Ativo; a Afya comprou a PEBMED em 2019. Em 2025 lançou o **Whitebook Assist** (IA conversacional com conteúdo de 200+ especialistas). |
| Público | Médicos e estudantes de medicina; a Afya diz que 1 em cada 5 usa o app. |
| Funcionalidades | Referência clínica (condutas, doses, bulário, calculadoras), conteúdo por especialidade, versão web, IA. |
| Verificação | Cadastro com CRM para o plano profissional; planos especiais para estudantes. |
| Modelo de negócio | Freemium com assinatura (em 2023: R$ 718,80/ano ou 12 × R$ 59,90). O Whitebook já respondeu por mais de 95% da receita da PEBMED (165 mil usuários ativos mensais na época da aquisição). |
| Pontos fracos | A Afya relatou **perda de assinantes do Whitebook por trimestres seguidos após o aumento de preço de 2024**. No 1º trimestre de 2026, os pagantes de *Medical Practice Solutions* caíram 1% (201 mil) e os usuários ativos mensais 10% (221 mil), com a concorrência de **LLMs públicos gratuitos** citada como causa. |
| Lição para o DE | Conteúdo de referência virou commodity com a IA generativa. O valor duradouro está na **rede** (quem eu conheço e consulto) e no **registro** (o que fica no prontuário). |

### 5.3 iClinic (Afya)

| Aspecto | Achado |
|---|---|
| Status | Ativo; fundado em 2012, comprado pela Afya em 2020 (R$ 182,7 mi). Lançou o **iClinic Assist** (grava, transcreve e resume a consulta). |
| Público | Médicos de consultório e clínicas pequenas e médias. |
| Funcionalidades | PEP, agenda, prescrição, financeiro, telemedicina, IA de documentação. |
| Modelo de negócio | SaaS por assinatura do médico ou da clínica. |
| Pontos fracos e reclamações | Reclame Aqui (fev–jul/2026): atendimento "Regular", 79 reclamações, 70% resolvidas, ~17 dias para responder. Relatos de **conta cancelada sem pedido e perda de acesso aos prontuários**, **bloqueio do PEP por cobrança irrisória**, cobranças e upgrades indevidos, registro de paciente que teria sumido. |
| Lição para o DE | Bloquear o acesso ao prontuário por questão de cobrança é risco ético e legal (guarda de 20 anos, Lei 13.787/2018). O DE deve garantir acesso de leitura e exportação ao PEP **independente** do status de pagamento. |

### 5.4 Amplimed (Raia Drogasil)

| Aspecto | Achado |
|---|---|
| Status | Ativo; comprado pela **RD (Raia Drogasil)** em dez/2021. Termos de uso atualizados em dez/2025. |
| Público | Clínicas e consultórios; contrato institucional com a Unimed Fesp (2020). |
| Funcionalidades | Agenda online, PEP com módulos por especialidade, telemedicina, financeiro, faturamento TISS, pedido de exames. **Ampli Prescreve** é gratuito para qualquer profissional de saúde. |
| Modelo de negócio | SaaS por plano; prescrição gratuita como porta de entrada; sinergia com a rede de farmácias da RD. |
| Lição para o DE | Varejo farmacêutico usa prescrição grátis como canal. Isso reforça que a **prescrição não é fonte de receita** para o DE; é recurso de retenção. |

### 5.5 Memed

| Aspecto | Achado |
|---|---|
| Status | Ativa; atingiu o **breakeven em jun/2025** e projeta R$ 100 mi de receita em 2026. Já captou R$ 80 mi e R$ 300 mi em rodadas anteriores. |
| Público | Médicos e outros prescritores; farmácias; integra-se a sistemas de prontuário. |
| Funcionalidades | Prescrição digital com base de medicamentos, assinatura digital, envio ao paciente por **e-mail, WhatsApp ou SMS**; desde set/2025, envio por **RCS** com botão para salvar a receita na **Google Wallet** (PoC: 50% de engajamento, 10% salvaram). |
| Verificação | Cadastro de prescritor; a norma do CFM exige que plataformas de prescrição confirmem a regularidade do médico pelo Cadastro Nacional do CFM ou por certificado de atributo (método exato da Memed **não confirmado**). |
| Modelo de negócio | **Gratuito para médicos, pacientes e farmácias.** Segundo o CEO, a receita vem da **indústria farmacêutica** por banners e *retail media*. Números de alcance variam: 150 mil a 260 mil+ médicos, 4,7 mi de prescrições por mês, 80 mil+ farmácias (datas incertas). O CEO estima que só 15% das prescrições no Brasil sejam digitais. |
| Pontos fracos | Não foram encontradas reclamações sistemáticas. O risco do modelo é a percepção de conflito de interesse: a Res. CFM 1.595/2000 veda vincular a prescrição a vantagens da indústria, e publicidade **no momento da prescrição** é sensível. |
| Lição para o DE | Integrar com a Memed (ou com o SNCR direto) pode ser mais rápido que construir catálogo de medicamentos. Evitar anúncio no fluxo de prescrição é um diferencial ético que dá para comunicar. |

### 5.6 Prescrição Eletrônica CFM (e Atesta CFM)

| Aspecto | Achado |
|---|---|
| Status | Ativa; desenvolvida pelo CFM com o CFF (farmácia) e o ITI. Gratuita e válida em todo o país. |
| Funcionalidades | Oito documentos: receita simples, receita de antimicrobiano, receita de controle especial, pedido de exames, atestado, relatório, laudo e parecer técnico. Assinatura com o **certificado ICP-Brasil em nuvem gratuito** do CFM (emitido pelo CRM Virtual; quem não tem biometria atualizada precisa ir ao CRM). **Validador** oficial de receitas e atestados; **Atesta CFM** para emissão e validação de atestados. |
| Verificação | Inscrição ativa e adimplente no CRM. |
| Modelo de negócio | Serviço público do conselho; adesão não obrigatória para médico, farmácia ou estabelecimento. |
| Pontos fracos | Ferramenta isolada: não tem rede, prontuário nem integração de fluxo; a experiência de uso é pouco discutida em fontes públicas. |
| Lição para o DE | O **certificado gratuito do CFM** elimina a barreira de custo do ICP-Brasil. O RF09 deve aceitar esse certificado em nuvem desde o primeiro dia. Ser compatível com o validador do CFM/ITI é obrigatório. |

### 5.7 Doctoralia (Docplanner)

| Aspecto | Achado |
|---|---|
| Status | Ativa; grupo Docplanner (Polônia). Em 2018 já tinha 5 mi de usuários ativos no Brasil. Lançou o **Noa Notes** (IA que resume a consulta, compatível com prontuário próprio ou externo); a adoção informada varia de 4 mil a 30 mil+ médicos pagantes, dependendo da fonte. |
| Público | **Pacientes** (busca, agendamento, avaliações) e médicos e clínicas (agenda, prontuário, telemedicina, marketing). |
| Funcionalidades | Perfil público com avaliações, agendamento online, lembretes, telemedicina, prontuário, IA de documentação. |
| Modelo de negócio | Assinatura do médico ou da clínica pelos planos de visibilidade e gestão. |
| Pontos fracos e reclamações | Reclame Aqui: reputação "Boa" (7,2/10, 699 reclamações, 83% respondidas). Portal da Queixa: índice de 22/100. Temas: **cobranças contestadas** e dificuldade de mudar o plano; acusação de que **avaliações negativas de médicos pagantes não são publicadas** (caso individual; a empresa diz moderar com filtros automáticos e revisão humana). |
| Compliance | Avaliações de pacientes e perfil público esbarram nas regras de publicidade médica (Res. CFM 2.336/2023). |
| Lição para o DE | O DE fica longe do marketplace de pacientes no MVP. Se um dia abrir para pacientes, a transparência na moderação de avaliações é a maior fonte de desconfiança. |

### 5.8 Conexa Saúde

| Aspecto | Achado |
|---|---|
| Status | Ativa; fundiu-se com a Zenklub (saúde emocional, fim de 2023; dependia do Cade) e comprou a iMedicina. Diz ter 30–35 mi de vidas, 7,2 mi de consultas por ano, 6 mil profissionais, 1.300 a 10 mil empresas e 192 operadoras. Em 2025 anunciou o "Hospital Conexa" e meta de R$ 300 mi de receita. |
| Público | Empresas, operadoras e pacientes (B2B2C); profissionais contratados ou credenciados. |
| Funcionalidades | Pronto atendimento digital, especialidades, psicologia, gestão de saúde populacional. |
| Modelo de negócio | Contratos B2B por vida ou por uso. |
| Relação com o DE | Adjacente: a Conexa é cliente potencial (corpo clínico disperso precisa de comunicação segura e interconsulta), não concorrente direto. Não foi encontrada nenhuma fusão Conexa–Docway. |

### 5.9 Docway (SulAmérica)

| Aspecto | Achado |
|---|---|
| Status | Ativa; empresa do **grupo SulAmérica** (que desde 2022 integra a Rede D'Or). Comprou a Dr. Vem. Parceria com a Oi (2023): pronto atendimento digital a R$ 19,90/mês, 4 mil+ médicos, 25 especialidades. Clientes corporativos como BRF, Azul e Camed. |
| Público | Beneficiários de operadoras e empresas; médicos da rede própria. |
| Modelo de negócio | B2B (operadoras, empresas, telecom). |
| Relação com o DE | Igual à Conexa: telemedicina com o paciente está fora do MVP do DE; esses grupos são parceiros ou clientes do módulo institucional. |

### 5.10 Portal Telemedicina

| Aspecto | Achado |
|---|---|
| Status | Ativa; apresentou atualizações de IA e IoT na Feira Hospitalar 2024. |
| Funcionalidades | Laudo a distância em 11 modalidades (ECG, EEG, espirometria, mamografia, TC, RM, raio-X e outras), entrega no mesmo dia, urgência em horas, **IA que prioriza a fila** de exames com achados suspeitos. |
| Modelo de negócio | Serviço por exame ou contrato com clínicas, hospitais e prefeituras. |
| Ressalva | As alegações sobre IA vêm de material da própria empresa; não foram encontrados estudos independentes de validação. |
| Lição para o DE | Mostra demanda por **segunda opinião remota estruturada** no interior. A interconsulta entre pares do DE pode atender casos que não são laudo de exame. |

### 5.11 Sistemas hospitalares: MV e Tasy

| Aspecto | MV (SOUL MV) | Tasy |
|---|---|---|
| Status | Líder na América Latina segundo o KLAS 2025 e 5º maior fornecedor global de EHR hospitalar. Receita líquida de R$ 741 mi (+20%); meta de R$ 1 bi em 2027; R$ 80 mi+ por ano em P&D. Aposta de 2026: **SOUL Agents** (IA agêntica). | 2º mais usado na América Latina (KLAS 2025), ~1.000 instituições. **A Philips anunciou a venda à Bionexo em 30/12/2025** (~€ 161 mi ≈ R$ 1 bi), sujeita à aprovação regulatória; fechamento previsto até o 2º trimestre de 2026 **(conclusão não confirmada)**. Primeiro PEP regulado como dispositivo médico pela Anvisa. |
| Público | Hospitais, clínicas, operadoras e redes públicas (~2,6 mil clientes em 2022). | Hospitais, clínicas e operadoras. |
| Compliance | PEP com certificação SBIS/CFM; apoio a certificações HIMSS EMRAM 6/7. | Registro na Anvisa; certificações SBIS. |
| Pontos fracos (gerais do segmento) | Implantação longa e cara; customização; pouca usabilidade móvel; comunicação entre médicos fica fora do sistema (WhatsApp). A incerteza da venda do Tasy gerou alerta entre hospitais clientes. | |
| Relação com o DE | **Parceiros de integração, não concorrentes.** O DE precisa trocar dados com o PEP institucional (HL7 FHIR/openEHR) para que a nota SOAP e a receita não fiquem num "PEP paralelo". | |

### 5.12 Grupos de WhatsApp médicos

| Aspecto | Achado |
|---|---|
| Uso | Canal dominante para discutir casos, passar plantão, pedir parecer e encaminhar pacientes. O Parecer CFM 14/2017 (citado por fontes secundárias) considera permitido usar WhatsApp em **grupos fechados só de médicos registrados**, com sigilo restrito ao grupo e proibição de circular em grupos recreativos. O CRM-PR (2019) condicionou a inclusão de enfermeiros a parecer próprio. |
| Pontos fortes | Todos já estão lá (efeito de rede pronto), é rápido, gratuito, tem criptografia ponta a ponta, mídia e voz. |
| Pontos fracos | Ninguém verifica quem é médico; não há anonimização (foto com rosto, nome no prontuário, metadados); imagens vão para a galeria do celular e o backup na nuvem; não há moderação nem auditoria; não há retenção controlada; o parecer dado no grupo não vira registro no prontuário; misturam-se assuntos clínicos e pessoais. |
| Incidentes | Dados de saúde da ex-primeira-dama Marisa Letícia (2017) circularam de forma identificada em grupo de médicos e chegaram à imprensa; o Cremesp investigou. Em 2022, um cardiologista de Goiás expôs dados e foto de um idoso num grupo de 214 médicos. Um estudo irlandês com médicos não consultores mostrou que, mesmo após treinamento e oferta de app seguro, o compartilhamento de informação identificável **só caiu de 97% para 81%**. |
| Compliance | LGPD (dado sensível, Art. 11), sigilo médico (Código de Ética), Res. CFM 1.821/2007. O vazamento pode gerar processo ético no CRM e responsabilidade civil. |
| Lição para o DE | O concorrente real é o WhatsApp. Ganhar exige paridade de velocidade e mais valor clínico, não só conformidade. |

---

## 6. (a) Tabela comparativa: funcionalidades × produtos

**Legenda:** ● tem · ◐ parcial ou depende do plano · ○ não tem · ? não confirmado nesta pesquisa.
Baseada em informação pública de out/2026; recursos mudam rápido e precisam ser revalidados antes de uso
externo.

| Produto | Verificação de registro obrigatória | Feed / comunidade | Casos com anonimização de mídia | Grupos fechados por especialidade ou organização | Chat seguro 1:1 e grupo | E2EE declarada | Vídeo / voz | Contato com paciente (telefone mascarado ou teleconsulta) | Prescrição / documento assinado | PEP / registro clínico | Diretório institucional / corpo clínico | Integração EHR | IA (scribe ou referência) | Grátis para o médico | Foco Brasil (CRM, ICP, LGPD) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **Doctor Explorer (MVP)** | ● CRM + RQE | ● | ● automático + revisão | ● | ● | ● MLS | ● SFrame | ○ (fora do MVP) | ● ICP-Brasil PAdES | ● openEHR, 20 anos | ● CNPJ + CNES | ◐ planejada | ○ (CDS fora do MVP) | ? (modelo em aberto) | ● |
| Doximity | ● NPI + documento | ● | ○ | ◐ | ● | ? | ● | ● Dialer | ◐ fax, cartas | ○ | ◐ perfis, hospitais | ◐ | ● Scribe, GPT | ● | ○ |
| Figure 1 | ● para postar | ● | ● revisão humana | ◐ | ◐ | ? | ○ | ○ | ○ | ○ | ○ | ○ | ? | ● | ○ |
| Sermo | ● tripla | ● | ? | ◐ | ◐ | ? | ○ | ○ | ○ | ○ | ○ | ○ | ? | ● (paga o médico) | ○ |
| Medscape Consult | ◐ perfil médico | ● | ? | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ? | ● | ○ |
| MedShr | ● | ● | ● + consentimento no app | ◐ | ◐ | ? | ○ | ○ | ○ | ○ | ○ | ○ | ? | ● | ○ |
| Doctolib Siilo | ● | ○ | ◐ casos de paciente | ● | ● | ? | ● | ○ | ○ | ○ | ◐ | ◐ ecossistema Doctolib | ? | ● | ○ |
| Pando | ● | ◐ fóruns | ◐ câmera dentro do app | ● | ● | ? | ? | ○ | ○ | ○ | ● diretório do trust | ? | ? | ● | ○ |
| TigerConnect | ◐ institucional | ○ | ○ | ● por papel | ● | ? | ● | ◐ | ○ | ○ | ● | ● | ? | ○ | ○ |
| Vocera (Stryker) | ◐ institucional | ○ | ○ | ● por papel | ● | ? | ● crachá | ○ | ○ | ○ | ● | ● Epic | ? | ○ | ○ |
| Epic Secure Chat | ◐ usuário do EHR | ○ | ○ | ● | ● | ? | ◐ | ◐ via Epic | ● no EHR | ● no EHR (o chat não é registro) | ● | ● nativo | ◐ | ○ | ○ |
| Microsoft Teams | ○ conta corporativa | ◐ canais | ○ | ● | ● | ◐ opcional | ● | ◐ Bookings + Premium | ○ | ○ | ● diretório corporativo | ◐ Epic | ● Dragon Copilot | ◐ no M365 | ○ |
| Pulsara | ◐ por organização | ○ | ◐ imagens do caso | ● canal por paciente | ● | ? | ● | ○ | ○ | ◐ dados do caso, API NEMSIS | ● | ◐ ePCR, ZOLL | ? | ● EMS / ○ hospital | ○ |
| Whitebook | ◐ CRM no plano | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ● Assist | ◐ freemium | ● |
| iClinic | ◐ cadastro | ○ | ○ | ○ | ○ | ○ | ◐ teleconsulta | ● | ● | ● | ○ | ◐ | ● Assist | ○ | ● |
| Amplimed | ◐ cadastro | ○ | ○ | ○ | ○ | ○ | ● teleconsulta | ● | ● Ampli Prescreve (grátis) | ● | ○ | ◐ TISS | ? | ◐ | ● |
| Memed | ● prescritor | ○ | ○ | ○ | ○ | ○ | ○ | ◐ envio da receita | ● | ○ | ○ | ● com PEPs | ? | ● | ● |
| Prescrição CFM | ● CRM ativo | ○ | ○ | ○ | ○ | ○ | ○ | ◐ envio da receita | ● 8 documentos, certificado grátis | ○ | ○ | ○ | ○ | ● | ● |
| Doctoralia | ◐ | ○ | ○ | ○ | ○ | ○ | ● teleconsulta | ● | ◐ | ● | ◐ perfis públicos | ◐ | ● Noa Notes | ○ | ● |
| Conexa / Docway | ◐ rede própria | ○ | ○ | ○ | ○ | ○ | ● | ● | ● | ◐ | ○ | ◐ | ? | n/a (médico contratado) | ● |
| Portal Telemedicina | ◐ rede própria | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ● laudos | ◐ | ○ | ◐ PACS | ● triagem | n/a | ● |
| MV / Tasy | ◐ usuário do hospital | ○ | ○ | ○ | ◐ | ○ | ◐ | ◐ | ● | ● | ● | ● nativo | ◐ SOUL Agents | ○ | ● |
| WhatsApp (grupos) | ○ | ◐ grupos e comunidades | ○ | ◐ fechado, sem verificação | ● | ● Signal | ● | ● (uso informal) | ○ | ○ | ○ | ○ | ○ | ● | ○ (sem garantias de conformidade) |

**Leitura da tabela.** Só o Doctor Explorer marca, ao mesmo tempo, verificação por conselho, comunidade de
casos com anonimização, comunicação E2EE e ato clínico formal. Os pontos em que o DE começa **atrás** são
contato com o paciente, IA e integração com EHR: os três foram adiados de propósito no MVP, mas viraram
expectativa do mercado (ver §8).

---

## 7. (b) Lacunas de mercado que o Doctor Explorer pode ocupar no Brasil

| # | Lacuna | Evidência | Como o DE ocupa |
|---|---|---|---|
| 1 | **Não existe rede profissional médica brasileira verificada por CRM/RQE** | A busca não achou nenhuma; Doximity é só EUA; Figure 1 e Sermo não verificam CRM. | RF02 com o webservice oficial do CFM (R$ 948/ano) + reverificação diária. Selo como ativo de reputação. |
| 2 | **Substituto conforme e melhor que o WhatsApp** para grupos médicos | Parecer CFM 14/2017 limita os grupos a médicos registrados, mas ninguém verifica; incidentes recorrentes; o estudo irlandês mostra que só oferecer app não basta. | Grupos com entrada por RQE, câmera própria com anonimização, notificações sem conteúdo, retenção configurável, auditoria, moderação. Paridade de velocidade com o WhatsApp. |
| 3 | **Ponte entre discussão informal e registro formal** | Siilo tem "casos de paciente", mas não grava no prontuário; Epic separa o chat do registro; no Brasil, o parecer dado no WhatsApp não vai a lugar nenhum. | RF08: a nota SOAP nasce da discussão e vai ao PEP com o link do post; parecer assinado. **Nenhum concorrente faz isso.** |
| 4 | **Interconsulta e parecer estruturado entre médicos de instituições diferentes** | Medscape Consult provou demanda (90% dos casos respondidos), mas era informal; Portal Telemedicina cobre só laudo de exame; 4.895 municípios têm 8% dos médicos. | Pedido de parecer com especialidade, prazo e resposta registrada; métrica de tempo de resposta (já no RF13). |
| 5 | **Diretório B2B de corpo clínico verificado** | Doctoralia é vitrine para pacientes, com avaliações contestadas; MV e Tasy conhecem só o corpo clínico do próprio hospital. | RF11: instituição validada por CNPJ + CNES e vínculo aceito pelos dois lados. Serve para credenciamento, escala e encaminhamento entre hospitais. |
| 6 | **Modelo de negócio sem conflito com a indústria** | Doximity, Figure 1, Memed, OpenEvidence e Docquity vivem de pharma; Res. CFM 1.595/2000; LGPD exige consentimento para marketing. | Receita B2B (hospitais, redes, operadoras pagam pelo módulo institucional) + uso gratuito para o médico. Comunicar "sem anúncio no caso clínico nem na prescrição". |
| 7 | **Confiança no acesso ao próprio prontuário** | Reclamações no iClinic sobre bloqueio do PEP por cobrança e cancelamento indevido. | Leitura e exportação do PEP garantidas independentemente de pagamento; exportação em JSON versionado (RF10); guarda de 20 anos com cofre clínico separado. |
| 8 | **Rede multiprofissional verificada** | Pando e Siilo aceitam enfermagem e outras profissões; no Brasil, nenhuma rede verifica COREN, CRF ou CREFITO. | Selo por conselho (já previsto no ator "Outro profissional de saúde" ⚖️). Começar por enfermagem e farmácia hospitalar. |
| 9 | **Prescrição integrada ao fluxo de discussão e, depois, ao SNCR** | A RDC 1.000/2025 obriga integração com o SNCR para controle especial; Memed e CFM resolvem a prescrição isolada. | RF09 aceitando o certificado gratuito do CFM; roadmap de integração com o SNCR e a RNDS. |
| 10 | **Educação baseada em casos reais brasileiros** | Figure 1 mostrou que o feed educativo retém; o Whitebook perde assinantes para LLMs. | Feed de casos anonimizados com curadoria de sociedades de especialidade; certificados de participação (validar com AMB/CNA para pontuação). |

---

## 8. (c) Funcionalidades que viraram padrão

Sem estes itens o produto parece incompleto ao usuário que já usa Doximity, Siilo, WhatsApp ou Memed.

### 8.1 Obrigatórias no lançamento

| Funcionalidade | Quem estabeleceu o padrão | Situação no DE |
|---|---|---|
| Verificação **em minutos**, automática, com revisão manual só para exceções | Doximity, OpenEvidence | RF02 prevê; definir meta de tempo (ex.: P90 < 10 min para CRM ativo) |
| **Perfil pré-preenchido** a partir do registro público | Doximity (NPI) | Avaliar com dados públicos do CFM ⚖️ (LGPD Art. 7º §3º e §4º: finalidade compatível) |
| Chat 1:1 e em grupo com mídia, confirmação de leitura, push confiável, **desktop + mobile** | WhatsApp, Siilo, TigerConnect | RF06; confiabilidade de push é crítica (reclamações do TigerConnect) |
| **PIN/biometria** e **notificações sem conteúdo** | Siilo | Incluir no RF06 |
| **Câmera dentro do app**, sem salvar na galeria, com borrar rosto e remover metadados | Pando (após incidente), Figure 1, MedShr | RF05; garantir que a mídia nunca toque o rolo da câmera |
| **Termo de consentimento do paciente** dentro do app | MedShr | Avaliar para casos com imagem identificável ⚖️ |
| Voz e vídeo a partir da conversa | Siilo, Doximity | RF07 |
| Busca de colegas por nome, CRM, especialidade e instituição | Doximity, Pando | RF03 + RF11 |
| Status de disponibilidade e "não perturbe" por plantão | Pando, TigerConnect | Não previsto: incluir |
| Prescrição digital **gratuita**, enviada por WhatsApp, SMS ou e-mail, com QR de validação | Memed, CFM, Ampli Prescreve | RF09; envio ao paciente já previsto |
| Exportação dos próprios dados | LGPD, GDPR | RF10 |

### 8.2 Esperadas em pouco tempo (a primeira pergunta do usuário depois do lançamento)

| Funcionalidade | Quem estabeleceu o padrão | Comentário |
|---|---|---|
| **Scribe com IA gratuito** | Doximity Scribe, iClinic Assist, Doctoralia Noa, Dragon Copilot | Está fora do MVP (CDS); planejar com as salvaguardas do §2.1 da especificação |
| **Referência clínica com IA** | OpenEvidence, Doximity GPT/Pathway, Whitebook Assist | Integrar ou fazer parceria em vez de construir |
| Ligação ao paciente com **número mascarado** | Doximity Dialer | É o recurso que mais traz uso diário na Doximity; avaliar para depois do MVP (Res. CFM 2.314/2022) |
| Comunicação **centrada no paciente** (canal por caso) | Pulsara, Siilo | O post de caso + SOAP do DE já é parecido; dá para expor como "canal do caso" |
| Integração com o PEP hospitalar (FHIR) | Epic, Vocera Edge, TigerConnect | Necessária para vender a hospitais com MV ou Tasy |
| Receita de controle especial via SNCR | Anvisa RDC 1.000/2025 | Fora do MVP ⚖️; virará requisito de paridade com Memed e CFM |

---

## 9. (d) Lições sobre adoção e efeitos de rede

### 9.1 Como a Doximity cresceu

1. **Semente a partir do registro público.** Perfis pré-criados com dados do NPI e do Medicare; o médico só
   reivindica. Tira o problema da "rede vazia".
2. **Grafo pronto da profissão.** Poucas escolas médicas e residências tornam a profissão muito conectada:
   em 2013, cada médico tinha cerca de 25 colegas mapeados e havia ~10 mi de conexões. Indicações entre
   médicos geravam **80% dos novos usuários**, e o fundador dizia "estamos ganhando por efeito de rede".
3. **Utilidade individual antes da rede.** Fax digital, Dialer com o número do consultório, telemedicina na
   COVID e, em 2025, Scribe e GPT gratuitos. O médico abre o app para trabalhar, não para "socializar".
4. **Gratuito para o médico, cobrado de quem quer falar com ele.** Pharma, sistemas de saúde e
   recrutadores pagam. 1 mi de membros em 2018 e 3 mi+ em 2026.
5. **Custo:** fadiga de e-mail e a percepção de que a atenção do médico é vendida. Um produto brasileiro
   pode se diferenciar evitando isso.

### 9.2 Como o Figure 1 cresceu

1. **Conteúdo de alto valor e fácil de consumir:** imagens clínicas raras, no formato de rede social que
   todos já conhecem ("Instagram dos médicos").
2. **Leitura ampla, escrita só para verificados:** cresce a audiência sem perder a confiança no que é
   postado.
3. **Moderação e desidentificação humanas** desde o início, o que deu segurança a instituições e a quem
   posta.
4. **Monetização pelo formato nativo** (conteúdo patrocinado par a par) só depois de 1 mi de usuários.
5. **Resultado:** acabou absorvido por um grupo de mídia médica (Formedics). Comunidade é ótimo motor de
   audiência, mas, sozinha, é um negócio de mídia.

### 9.3 Por que outros falharam ou estagnaram

| Caso | O que aconteceu | Lição |
|---|---|---|
| **Hospify** (2014–2022) | A demanda dependia da fiscalização de proteção de dados; quando ela afrouxou, o crescimento colapsou. | Conformidade sozinha não vende. É preciso ser melhor que o WhatsApp. |
| **QuantiaMD** (extinto) | Rede de 200 mil médicos com conteúdo coproduzido e pago pela indústria; comprada por uma empresa da Merck e depois descontinuada. | Rede financiada por pharma perde a confiança e a razão de existir. |
| **Medscape Consult** | 310 mil acessaram, 12% foram ativos, 90% dos ativos só respondiam. | A maioria nunca posta: desenhar para leitores e respondedores e medir o tempo até a primeira resposta. |
| **Pando** | Fotos iam para a galeria do celular; uma sociedade médica retirou o endosso até a correção. | Uma falha de privacidade apaga anos de confiança. Câmera própria é obrigatória. |
| **Siilo** | Cresceu como "WhatsApp dos médicos" grátis, mas foi vendido a um ecossistema de agenda e prontuário. | Mensageria gratuita precisa de um negócio por trás. |
| **Whitebook** | Perde assinantes desde o aumento de preço de 2024, com a concorrência de LLMs gratuitos. | Conteúdo virou commodity; rede e registro, não. |
| **Secure chats hospitalares** | Mensagens enviadas à pessoa errada, excesso de mensagens, logouts e notificações falhando. | Comunicação clínica é questão de segurança do paciente; usabilidade conta como requisito. |
| **Apps "seguros" contra o WhatsApp** | Mesmo com app e treinamento, o compartilhamento identificável só caiu de 97% para 81%. | É preciso combinar mandato institucional, paridade de velocidade e valor clínico extra. |

### 9.4 Estratégia de adoção sugerida para o DE

1. **Entrar pelo hospital, crescer pela especialidade.** O contrato institucional (RF11–RF13) cria a
   densidade local que fez TigerConnect e Vocera funcionarem; os grupos por especialidade criam a ponte
   entre hospitais, que é o efeito de rede da Doximity.
2. **Semear o grafo:** importar o corpo clínico (com aceite dos dois lados), convites por CRM e por grupo
   de WhatsApp existente ("migre seu grupo"), perfil pré-preenchido com dados públicos do CFM ⚖️.
3. **Uma utilidade diária desde o primeiro dia:** a candidata mais forte é a **receita ICP-Brasil grátis
   com o certificado do CFM**, integrada à discussão do caso. Depois, scribe e ligação com número mascarado.
4. **Parceria com sociedades de especialidade e residências**, como faz a Docquity (250+ associações), para
   curadoria de grupos e conteúdo educativo.
5. **Métricas de rede:** tempo até a primeira resposta de um caso, % de casos respondidos em 24 h, colegas
   conectados por usuário nos primeiros 7 dias, % de grupos de WhatsApp migrados por instituição.

---

## 10. (e) Tipos de usuário atendidos por esses produtos

| Tipo de usuário | Necessidade principal | Produtos que atendem hoje | Status no DE |
|---|---|---|---|
| Médico especialista em consultório | Prontuário, prescrição, agenda, segunda opinião | iClinic, Amplimed, Doctoralia, Memed | Núcleo do MVP |
| Médico hospitalista / plantonista | Comunicação rápida com equipe, passagem de plantão, parecer | TigerConnect, Epic Secure Chat, Vocera, WhatsApp | Núcleo do MVP (chat, grupos); falta status de plantão |
| Residente e médico jovem | Tirar dúvidas, aprender com casos, carreira | Figure 1, Medscape Consult, Whitebook, Doximity (residência) | Público de entrada (posta mais casos) |
| Médico sênior / preceptor | Ensinar, responder, reputação | Figure 1, Sermo, Medscape Consult | Núcleo (respondedor) |
| Estudante de medicina | Educação, referência | Whitebook, Figure 1, MedShr, Doximity (estudantes) | Fora do MVP ⚖️ (exige outro tipo de verificação) |
| Médico de cidade pequena ou interior | Acesso a especialista, interconsulta | Portal Telemedicina, Telessaúde, WhatsApp | Lacuna a ocupar (§7, item 4) |
| Enfermagem | Coordenação de cuidado, alarmes, mensagens à equipe médica | Vocera, TigerConnect, Epic Rover, Pando, Siilo | Ator previsto ⚖️ |
| Farmacêutico | Validar receitas, interações, conversar com o prescritor | Memed, validador CFM/ITI, Doximity (farmacêuticos) | Ator previsto ⚖️ |
| Outras profissões (fisioterapia, psicologia, nutrição) | Encaminhamento, discussão multiprofissional | Conexa, Doctoralia, Siilo | Ator previsto ⚖️ |
| NP / PA (EUA), sem equivalente direto no Brasil | Prescrição e cuidado com supervisão | Doximity, OpenEvidence | Não se aplica |
| Socorrista / EMS | Pré-notificação, ECG, ativação de protocolo | Pulsara, TigerConnect (Twiage) | Fora do escopo |
| Gestor hospitalar / diretor clínico | Corpo clínico, conformidade, indicadores | MV, Tasy, TigerConnect, Teams | Núcleo (RF11–RF13) |
| TI e segurança da informação do hospital | Integração, SSO, auditoria, BAA/LGPD | Teams, Epic, Vocera Edge | Precisa de SSO/SAML e trilhas de auditoria |
| Central de regulação e transferências | Encaminhar pacientes entre unidades | TigerConnect (Twiage), Pulsara | Futuro (diretório + parecer) |
| Operadora de saúde e empresa contratante | Telemedicina, custo, rede | Conexa, Docway, Amplimed | Cliente B2B potencial |
| Indústria farmacêutica e medtech | Alcançar médicos, pesquisa | Doximity, Sermo, Figure 1, Docquity, Memed, OpenEvidence | Fora do modelo por decisão ética (recomendação) |
| Recrutador / RH médico | Contratar médicos | Doximity Talent Finder, LinkedIn | Possível módulo futuro (vagas em instituições verificadas) |
| Sociedade de especialidade / associação | Comunidade, educação continuada | Docquity, Siilo (redes de associações) | Parceiro de canal |
| Paciente | Achar médico, agendar, receber receita | Doctoralia, Conexa, Docway, Memed | Fora do MVP como usuário ativo; titular dos dados (RF10) |

---

## 11. Implicações para a especificação do Doctor Explorer

Recomendações que saem desta pesquisa. Não alteram os documentos irmãos; servem de insumo para revisão.

1. **RF02 / ADR-009:** o webservice oficial do CFM existe, custa R$ 948/ano e devolve situação e
   especialidade (sem CPF). Confirma a opção "webservice contratado". Definir uma meta de tempo de
   verificação.
2. **RF05:** exigir **câmera nativa do app** (a mídia nunca vai para a galeria) e avaliar um **termo de
   consentimento** dentro do app para imagens potencialmente identificáveis (padrão MedShr; lição do Pando).
3. **RF06:** incluir **PIN/biometria**, **notificação sem conteúdo**, **status de plantão ou
   disponibilidade** e metas de confiabilidade de push.
4. **RF04:** medir **tempo até a primeira resposta** e desenhar para o respondedor (regra 1-9-90 do Medscape
   Consult). Fluxo explícito de "pedido de parecer" com especialidade e prazo.
5. **RF09:** suportar o **certificado em nuvem gratuito do CFM** e o validador CFM/ITI; preparar a
   integração com o **SNCR** (RDC 1.000/2025) para quando o controle especial entrar.
6. **PEP:** garantir **leitura e exportação** do prontuário independentes do status de pagamento (lição do
   iClinic) e documentar que o chat social não é registro clínico (lição do Epic: separar e informar).
7. **Modelo de negócio (Questões abertas):** recomendar receita B2B institucional e **nenhuma publicidade
   no fluxo clínico**, como diferencial ético e de conformidade (Res. CFM 1.595/2000, LGPD).
8. **Roadmap pós-MVP:** scribe com IA, referência clínica (parceria), ligação ao paciente com número
   mascarado, integração FHIR com MV/Tasy e RNDS.

---

## 12. Fontes

Acessadas em out/2026. Os documentos de empresas são autodeclarações.

### Doximity
- https://www.sec.gov/Archives/edgar/data/0001516513/000151651326000025/docs-20260331.htm (10-K FY2026)
- https://www.sec.gov/Archives/edgar/data/0001516513/000151651326000040/docs-20260630.htm (10-Q 1T FY2027)
- https://www.01net.it/doximity-announces-fourth-quarter-and-fiscal-year-2026-financial-results/
- https://s201.q4cdn.com/617428576/files/doc_financials/2026/q3/Doximity-3Q26-Earnings-Call-Prepared-Remarks.pdf
- https://support.doximity.com/hc/en-us/articles/24772213507731-How-To-Get-Verified-On-Doximity
- https://support.doximity.com/hc/en-us/articles/360047630573-How-to-Register-on-Doximity
- https://doximity.com/about/security
- https://www.doximity.com/clinicians/privacy
- https://www.businesswire.com/news/home/20250807262957/en/Doximity-Acquires-Pathway-a-Leader-in-AI-Clinical-Reference
- https://www.fiercehealthcare.com/health-tech/doximity-acquires-pathway-medical-63m-bolster-ai-tools-doctors
- https://www.fiercehealthcare.com/ai-and-machine-learning/doximity-jumps-ai-scribe-market-offering-free-tool-doctors
- https://blog.doximity.com/articles/meet-doximity-scribe
- https://techcrunch.com/2013/08/24/doximity/
- https://techcrunch.com/2014/03/15/with-40-of-u-s-doctors-signed-on-doximitys-jeff-tangney-reveals-how-the-social-network-for-m-d-s-hit-the-tipping-point
- https://caseworks.business.columbia.edu/caseworks/doximity-company-crossroads
- https://reddit.sentinel-team.org/posts/1pchnrs/snapshots/2025-12-05T08%3A40%3A44.658978Z (snapshot de thread do Reddit)
- https://reclaimthenet.org/?p=67606
- https://www.imedicalapps.com/2010/10/doximity-medical-app-facebook-for-physicians-iphone-review/

### Figure 1 / Formedics
- https://www.figure1.com
- https://www.figure1.com/news/celebrating-10-years-of-figure-1/
- https://en.wikipedia.org/wiki/Figure_1
- https://techcrunch.com/2017/04/19/figure-1-a-knowledge-sharing-app-for-doctors-launches-sponsored-content/amp/
- https://www.canhealth.com/2017/02/01/users-of-figure1-have-viewed-cases-2-billion-times/
- https://globalnews.ca/news/5134427/tech-in-t-o-figure-1/
- https://www.odwyerpr.com/story/public/21757/2024-08-28/formedics-acquires-amc-media.html
- https://www.cbinsights.com/investor/figure-1
- https://www.marsdd.com/news/figure1
- https://www.buscape.com.br/celular/conteudo/figure-1-o-aplicativo-instagram-dos-medicos-chegou-ao-brasil

### Sermo
- https://www.sermo.com/blog/is-sermo-legit
- https://en.wikipedia.org/wiki/Sermo
- https://www.sermo.com/membership/surveys/physicians/
- https://lp.sermo.com/2025-hcp-solutions
- https://www.trustpilot.com/review/sermo.com?page=8
- https://uk.trustpilot.com/review/sermo.com?page=2

### Medscape Consult
- https://www.webmd.com/corporate/press/20151111/medscape-launches-crowdsourced-clinical-knowledge-platform
- https://pmc.ncbi.nlm.nih.gov/articles/PMC6550165
- https://www.scripps.edu/news-and-events/press-room/2018/20181009-muse-medscape.html
- https://krs.libguides.com/mobile/medscape

### MedShr, Docquity, OpenEvidence, Pando, QuantiaMD
- https://en.medshr.net/pages/about
- https://en.medshr.net/open/medshr-app-doctors-live-us
- https://apps.apple.com/app/id1006385131
- https://www.mobihealthnews.com/news/asia/networking-platform-doctors-asia-scores-44m-series-c-funding
- https://www.biospectrumasia.com/index.php/opinion/54/25520/-there-is-a-need-for-pharmaceutical-companies-to-build-hcp-relationships-backed-by-scientific-credibility.html
- https://biospectrumasia.com/news/50/28479/docquity-extends-asian-healthcare-intelligence-to-japan-through-deepened-strategic-partnership-with-exmedio-.html
- https://en.wikipedia.org/wiki/OpenEvidence
- https://hlth.com/insights/news/openevidence-doubles-valuation-to-12bn-as-physician-adoption-accelerates-2026-01-22
- https://sacra.com/c/openevidence/
- https://www.beckershospitalreview.com/healthcare-information-technology/ai/openevidence-6-things-to-know-about-the-ai-tool-used-by-half-of-physicians/
- https://www.applytosupply.digitalmarketplace.service.gov.uk/g-cloud/services/363583651362190
- https://intercom.help/pando-b563d746beef/en/articles/4021068-how-is-pando-different-to-whatsapp
- https://www.mobihealthnews.com/news/emea/data-security-fears-raised-over-pando-app
- https://en.wikipedia.org/wiki/QuantiaMD
- https://www.fiercepharma.com/sales-and-marketing/merck-owned-physicians-interactive-scoops-up-digital-doc-community-to-bolster

### Siilo, Hospify
- https://www.siilo.com
- https://siilo.com/connect
- https://siliconcanals.com/doctolib-acquires-siilo/
- https://siliconcanals.com/siilo-rebrands-as-doctolib-siilo
- https://www.cbinsights.com/company/siilo
- https://en.wikipedia.org/wiki/Hospify
- https://digitalhealth.net/?p=130537
- https://www.digitalhealth.net/2020/03/hospify-becomes-first-nhs-approved-clinical-messaging-app/
- https://pharmaphorum.com/news/hospital-messaging-app-cleared-for-nhs-use/

### TigerConnect, Vocera, Epic, Teams, Pulsara
- https://www.businesswire.com/news/home/20241120649595/en/TigerConnect-Acquires-Twiage-Enhancing-Real-Time-EMS-to-Hospital-Communication-for-Streamlined-Emergency-Care
- https://www.privsource.com/acquisitions/deal/0oS64E
- https://www.businesswire.com/news/home/20240207451634/en/TigerConnect-is-a-2024-Best-in-KLAS-Award-Winner-for-Clinical-Communications
- https://www.capterra.com/p/180133/TigerConnect/reviews/1546236/
- https://www.softwareadvice.com/telemedicine/tigerconnect-profile/reviews/
- https://worldsapps.com/reviews-tigerconnect
- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12021371/ (JMIR 2025, mensagens internos × enfermagem)
- https://www.biospace.com/stryker-announces-definitive-agreement-to-acquire-vocera-communications
- https://www.stryker.com/us/en/smart-care/products/vocera-edge.html
- https://www.stryker.com/content/dam/stryker/acute-care/vocera/resources/solution-briefs/ca/vocera-edge-solution-brief_canada.pdf
- https://www.stryker.com/us/en/smart-care/products/vocera-smartbadge.html
- https://baptistcare.org.au/aged-care-homes/blog/new-vocera-badge-provides-faster-safer-care
- https://it.houstonmethodist.org/securechat/
- https://labs.northwell.edu/epic/tips/uploads/documents/8191091_One_Pager_Lab_Secure_Chat.pdf
- https://www.uchealth.org/employees/epic-rover-configurator/
- https://scholar.rochesterregional.org/advances/vol5/iss3/13
- https://scholarworks.uvm.edu/uvmahec/18
- https://learn.microsoft.com/vi-vn/microsoft-365/frontline/virtual-appointments-app?view=o365-worldwide
- https://support.office.com/en-us/article/7b8569e1-0c8a-444e-b712-d9968b05110b
- https://www.beckershospitalreview.com/?p=133287
- https://www.techtarget.com/enterprise-software/tip/Microsoft-Teams-has-HIPAA-collaboration-compliance-benefits-risks?amp=1
- https://www.plaud.ai/blogs/articles/dragon-copilot-review
- https://www.pulsara.com/packages
- https://www.pulsara.com/faqs/how-much-does-pulsara-cost
- https://www.ems1.com/ems-products/mobile-data/press-releases/pulsara-launches-12-lead-ekg-integration-as-part-of-expansive-integration-strategy-WocGBjSPutOolhc7/
- https://www.ems1.com/ems-products/communications/press-releases/pulsara-provides-nemsis-formatted-public-api-for-epcr-vendor-interoperability-a0BN0qxZaaru1peN/
- https://us.fitgap.com/products/024190/pulsara
- https://intuitionlabs.ai/software/emergency-medical-services-ems/hospital-notification-and-handoff/pulsara

### Brasil: contexto, CFM, Anvisa
- https://www.poder360.com.br/poder-saude/brasil-deve-chegar-a-635-706-medicos-em-2025/
- https://futurodasaude.com.br/?p=35759
- https://sistemas.cfm.org.br/listamedicos/informacoes
- https://sistemas.cfm.org.br/listamedicos/arquivos/manualwebservices.pdf
- https://portal.cfm.org.br/noticias/cfm-lanca-oficialmente-certificado-digital-gratuito-para-todos-os-medicos-brasileiros/
- https://portal.cfm.org.br/noticias/cfm-atualiza-plataforma-que-permite-aos-medicos-prescreverem-receitas-digitalmente/
- https://portal.cfm.org.br/noticias/cfm-cria-tutoriais-sobre-certificacao-e-prescricao/
- https://portal.cfm.org.br/noticias/entra-em-funcionamento-servico-que-permite-validar-receitas-medicas-e-atestados-digitais/
- https://portal.cfm.org.br/noticias/plataforma-atesta-cfm-recebe-elogios-de-autoridades-do-governo/
- https://www.coad.com.br/home/noticias-detalhe/109378/cfm-regulamenta-emissao-de-documentos-medicos-eletronicos
- https://portal.afya.com.br/saude/rdc-1-000-25-o-que-o-medico-precisa-saber-sobre-os-novos-receituarios-controlados
- https://portal.afya.com.br/saude/anvisa-adia-implementacao-nacional-de-receituarios-eletronicos-controlados
- https://site.cff.org.br/noticia/Noticias-gerais/08/04/2025/sncr-vai-permitir-emissao-eletronica-de-notificacoes-de-receitas-azuis-e-amarelas
- https://www.mprs.mp.br/noticias/28463/ (Res. CFM 1.595/2000)

### Brasil: produtos
- https://startupi.com.br/afya-anuncia-aquisicao-da-startup-produtora-de-aplicativos-com-foco-em-saude-pebmed/
- https://www.mobiletime.com.br/?p=669019
- https://portal.afya.com.br/carreira/med-friday-a-black-friday-do-whitebook
- https://vectorshift.ai/research/companies/afya/earnings/2025-q3
- https://vectorshift.ai/research/companies/afya/earnings/2026-q1
- https://www.businesswire.com/news/home/20250813566852/en (Afya 2T25)
- https://www.barchart.com/story/news/726130/afya-limited-announces-fourth-quarter-and-twelve-months-2025-financial-results
- https://startupi.com.br/afya-educacional-anuncia-aquisicao-de-healthtech-por-r-1827-milhoes/
- https://www.reclameaqui.com.br/empresa/iclinic/lista-reclamacoes/
- https://www.reclameaqui.com.br/iclinic/cancelamento-indevido-da-conta_EAdZT_ZqsDQmh6z9/
- https://www.reclameaqui.com.br/iclinic/bloqueio-injusto-de-prontuario-eletronico-por-pendencia-irrisoria-gera-insatisfacao-e-pedido-de-cancelamento-e-estorno_mlcgyNew6jZckIC2/
- https://www.amplimed.com.br/wp-content/uploads/2025/12/2025.12.01-Termos-e-Condicoes-Gerais-de-Uso-.pdf
- https://medicinasa.com.br/prescricao-digital-ampli
- https://cdn.rd.com.br/prod-v2/2022/06/c5e9d6de-20211222_aquisicoes-dezembro_aprovado.docx.pdf
- https://www.baguete.com.br/noticias/unimed-sp-telemedicina-e-prontuario-eletronico-com-amplimed
- https://startups.com.br/negocios/healthtechs/o-segredo-da-memed-para-deixar-de-queimar-caixa-e-mirar-o-unicornio/
- https://www.baguete.com.br/noticias/memed-recebe-aporte-de-r-300-milhoes
- https://www.mobiletime.com.br/noticias/29/09/2025/memed-rcs/
- https://certforum.iti.gov.br/wp-content/uploads/apresentacoes/21-09/05-GabrielCouto-Memed.pdf
- https://www.reclameaqui.com.br/empresa/doctoralia/
- https://portaldaqueixa.com/brands/doctoralia/about
- https://portaldaqueixa.com/brands/doctoralia/complaints/doctoralia-mentira-em-avaliacoes-nao-permitem-avaliacoes-negativas-de-medicos-pagantes-122890124
- https://www.mobiletime.com.br/noticias/02/05/2018/doctoralia-chega-a-5-milhoes-de-usuarios-ativos-no-brasil-em-11-meses/
- https://startupi.com.br/doctoralia-lanca-assistente-de-ia/
- https://medicinasa.com.br/?p=137858
- https://futurodasaude.com.br/entrevista-guilherme-weigert-brd-conexa/
- https://startups.com.br/podcast/podcast-mvp/apos-fusao-com-zenklub-conexa-mira-r-250-milhoes-de-receita-em-2024/
- https://medicinasa.com.br/?p=161826
- https://www.cnnbrasil.com.br/business/conexa-compra-imedicina-e-confirma-mercado-aquecido-de-startups-de-saude/
- https://teletime.com.br/11/01/2023/oi-oferta-servicos-de-telemedicina-em-parceria-com-a-docway/
- https://www.baguete.com.br/noticias/docway-adquire-dr-vem
- https://www.cbinsights.com/company/docway
- https://medicinasa.com.br/docway-telemedicina/
- https://portaltelemedicina.com.br/laudo-online-como-funciona-beneficios
- https://portaltelemedicina.com.br/?p=8629
- https://timesbrasil.com.br/brasil/bionexo-compra-tasy-da-philips-por-r-1-bilhao-e-reforca-lideranca-em-saude-digital/
- https://futurodasaude.com.br/?p=38177
- https://medicinasa.com.br/mv-klas-research/
- https://movimentoeconomico.com.br/geral/redacao/2026/07/27/com-quase-40-anos-de-historia-mv-impulsiona-a-transformacao-digital-da-saude-brasileira/
- https://itforum.com.br/instituicoes-do-sistema-unimed-assinam-contrato-com-mv/

### WhatsApp e privacidade na prática médica
- https://www.intramed.net/post/user_simple_post/claudio-orenstein-20260923-034840-683-pgrupo-de-whatsapp-de-medicos-parece-inofensivo-ma
- https://sistemas.cfm.org.br/normas/arquivos/pareceres/PR/2019/2771_2019.pdf (Parecer CRM-PR 2.771/2019)
- https://sanarmed.com/q/leia-com-atencao-o-parecer-abaixo-e-responda-o-que-se-pede-o-codigo-de-etica/
- https://www.conjur.com.br/2022-jul-16/sandra-franco-klara-castanho-direito-privacidade//?print=1
- https://www.mobiletime.com.br/noticias/10/03/2020/whatsapp-vira-meio-de-atendimento-para-65-dos-medicos-brasileiros/
- https://www.apm.org.br/wp-content/uploads/09_mai2020_Pesquisa-APM_medicos-e-covid19_02_tecnologias.pdf
- https://researchrepository.ul.ie/entities/publication/496d6364-43ee-4499-a310-93ee27cd565b
- https://www.digitalhealth.net/2024/02/restricting-doctors-use-of-whatsapp-is-pointless/
- https://fortune.com/2016/08/25/whatsapp-encryption-doctors-healthcare-hipaa
- https://confidata.com.br/blog/lgpd-clinicas-medicas-guia-adequacao-2026
