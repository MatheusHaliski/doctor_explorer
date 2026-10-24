# Questões abertas (precisam do dono do produto, do Diretor-Técnico, do DPO ou do jurídico)

Antes de inventar regra que depende do negócio, perguntamos. Cada item tem dono sugerido e o que bloqueia.

| # | Pergunta | Dono | Bloqueia |
|---|---|---|---|
| Q01 | Quem é a controladora, o Diretor-Técnico e o Encarregado (nome, CRM, contato)? | Produto | cabeçalho da especificação, página "Sobre", canal do DPO |
| Q02 | O MVP atende só médicos ou também outros profissionais de saúde (enfermagem, farmácia, fisioterapia…)? Quais conselhos verificar? | Produto + jurídico | RF02, matriz RF12, taxonomia de atores |
| Q03 | O paciente entra como usuário (painel de direitos, receber receita) ou só recebe link? | Produto + DPO | RF09 entrega, RF10 |
| Q04 | Qual fonte oficial do CFM podemos usar e em que condições (convênio, limites)? | Jurídico | ADR-009, RF02 |
| Q05 | A nota SOAP exige ICP-Brasil no MVP ou basta assinatura eletrônica avançada? | Diretor-Técnico | RF08 |
| Q06 | Receita de controle especial e atestado entram em qual fase? | Produto + Diretor-Técnico | RF09 |
| Q07 | Qual provedor de assinatura ICP-Brasil em nuvem (custo por assinatura, SLA)? | Produto | ADR-007 |
| Q08 | Prazo de retenção padrão das mensagens de chat (24 h / 7 d / 90 d / sem limite)? | DPO | RF06 |
| Q09 | Detecção de rosto em imagem dermatológica: opt-in ou opt-out? | Diretor-Técnico | RF05 |
| Q10 | Alertas de dose/alergia e calculadoras clínicas: aceitamos o enquadramento SaMD na Fase 2? | Regulatório | roadmap `cds` |
| Q11 | Licença SNOMED CT: contratar pela afiliação brasileira ou adiar? | Produto | ADR-005 |
| Q12 | Backup secundário fora do Brasil é aceitável (Art. 33) ou usamos duas regiões nacionais? | DPO + jurídico | ADR-011 |
| Q13 | Qual o prazo de resposta ao titular que vamos prometer (15 dias?) | DPO | RF10 |
| Q14 | Métricas do dashboard: a instituição pode ver atividade individual do seu corpo clínico ou só agregados? | DPO + Produto | RF13 |
| Q15 | Modelo de negócio (assinatura institucional, freemium, patrocínio de sociedade)? Impacta o que é "comunicação institucional" e marketing. | Produto | consentimentos RF10 |
| Q16 | Idioma secundário (inglês/espanhol) no MVP ou depois? | Produto | i18n |
| Q17 | Nome e cor da marca já estão fechados (azul #1565E0, verde, vermelho do logo)? | Produto | tokens |
| Q18 | Participação de residentes e acadêmicos (sem CRM): leitura só? grupo próprio? supervisão do preceptor? | Produto + Diretor-Técnico | RF02, RF04 |
