# Taxonomia de atores e cards

> A pesquisa aprofundada de tipos de usuário está em `docs/pesquisa/TIPOS_DE_USUARIOS.md`; este arquivo
> é a versão normativa que o código segue. Divergências entre os dois viram questão em
> [QUESTOES_ABERTAS.md](QUESTOES_ABERTAS.md).

## Atores

| Ator | Atributos que o sistema guarda | Papel global | Selo | 2FA |
|---|---|---|---|---|
| Visitante | nenhum (só IP para rate limit, hash) | — | — | — |
| Profissional pendente | nome, e-mail, nascimento, aceite dos Termos | `PROFESSIONAL` | NONE/PENDING | opcional |
| Médico verificado | + CRM/UF, RQE[], instituições, foto, bio | `PROFESSIONAL` | VERIFIED | recomendado |
| Gestor hospitalar | + vínculo com papel institucional | `PROFESSIONAL` | VERIFIED ou não (**⚖️** Q02) | **obrigatório** |
| Moderador / Admin | conta interna | `MODERATOR` / `ADMIN` | — | **obrigatório** |
| Encarregado (DPO) | conta interna | `DPO` | — | **obrigatório** |
| Diretor-Técnico | CRM, ato de designação | `TECHNICAL_DIRECTOR` | VERIFIED | **obrigatório** |
| Paciente (titular) | só no cofre clínico, cifrado | — (sem conta no MVP) | — | — |

## Tipos de card

Cards são as unidades visuais que o feed, os grupos e o dashboard mostram. Anatomia numerada para o
Design System e para o protótipo.

### Card de caso clínico

```
┌─────────────────────────────────────────────┐
│ ① Avatar  ② Nome · CRM/UF  ③ Selo  ④ Tempo  │
│ ⑤ Especialidade / grupo                      │
│ ⑥ Título do caso                             │
│ ⑦ Resumo (queixa + pergunta)                 │
│ ⑧ Mídia anonimizada (badge "anonimizada")    │
│ ⑨ Nº de pareceres  ⑩ Sem resposta?  ⑪ Ações │
└─────────────────────────────────────────────┘
```

| # | Elemento | Regra |
|---|---|---|
| ① | Avatar | foto limpa (sem EXIF); fallback com iniciais |
| ② | Nome · CRM/UF | sempre juntos (Res. CFM 2.336/2023) |
| ③ | Selo | cor + ícone + texto; só VERIFIED publica |
| ④ | Tempo | relativo ("há 2 h"), absoluto no tooltip |
| ⑤ | Especialidade | chip com link para o grupo |
| ⑥⑦ | Texto | passou pelo detector de identificadores |
| ⑧ | Mídia | badge "anonimizada" + relatório ao clicar |
| ⑨⑩ | Estado | "Sem resposta" em destaque neutro, nunca em vermelho |
| ⑪ | Ações | comentar, dar parecer, denunciar; "Registrar no prontuário" só para o autor |

### Card de parecer (comentário marcado como opinião técnica)

① avatar · ② nome · CRM/UF · ③ selo · ④ rótulo "Parecer" · ⑤ texto · ⑥ tempo · ⑦ ações (útil, responder,
denunciar).

### Card de instituição

① logo · ② nome · ③ tipo (rede/hospital/unidade) · ④ cidade/UF e distância · ⑤ nº de médicos verificados ·
⑥ selo "CNPJ e CNES conferidos" · ⑦ ação (ver, pedir vínculo).

### Card de documento clínico (receita / pedido / nota)

① tipo · ② paciente (iniciais + nascimento, só no PEP) · ③ data · ④ estado com cor + ícone + texto
(Rascunho, Assinada, Enviada, Cancelada…) · ⑤ hash curto · ⑥ ações (baixar, enviar, cancelar com motivo).

### Card de métrica (dashboard)

① título · ② valor agregado · ③ variação vs. período anterior · ④ "insuficiente" quando célula < 10 ·
⑤ link para a tabela acessível.

## Regras comuns

- Alvo de toque ≥ 44 px em toda ação do card.
- Nenhum card mostra dado de saúde identificável fora do PEP.
- Vermelho só em erro e ação destrutiva (heurística 8).
