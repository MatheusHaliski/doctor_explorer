# Avaliação heurística · MVP-1 (pré-avaliação)

**Estado:** pré-avaliação feita pela equipe de desenvolvimento sobre a web real (`apps/web`) em 2026-10-25.
A avaliação formal da §6.2 da especificação (3 a 5 avaliadores independentes, ao menos um médico e um
especialista em UX) **continua pendente** e substitui esta quando acontecer.

Severidade de 0 a 4 (0 não é problema · 1 cosmético · 2 menor · 3 maior · 4 catástrofe). Problema 3 ou 4
aberto bloqueia o release. O painel de qualidade (RF13) lê esta tabela: colunas `Média`, `Decisão` e `Estado`.

| # | Tela | Heurística | Descrição | Av. 1 | Av. 2 | Av. 3 | Média | Decisão | Estado |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Início (feed) | H2 · correspondência com o mundo real | Protocolo institucional aparece com o estado "Resolvido", termo de caso clínico que não faz sentido para protocolo. | 2 | 2 | 1 | 1.7 | backlog com prazo | aberto |
| 2 | Caso | H4 · consistência e padrões | Ocultar publicação (moderação) usa a caixa nativa do navegador para pedir o motivo, diferente dos diálogos do app. | 2 | 2 | 2 | 2.0 | backlog com prazo | aberto |
| 3 | Caso, segurança, gestão | H5 · prevenção de erros | Ações destrutivas confirmam com `confirm()` nativo, sem dizer o efeito exato nem oferecer desfazer. | 2 | 3 | 2 | 2.3 | backlog com prazo | aberto |
| 4 | Sessões e segurança | H6 · reconhecer em vez de lembrar | Ativar 2FA mostra o link otpauth e a chave em texto, sem QR code para escanear no celular. | 2 | 2 | 3 | 2.3 | backlog com prazo | aberto |
| 5 | Topbar | H10 · ajuda e documentação | Selo "Sem verificação" não leva à tela de envio de CRM; o usuário novo precisa descobrir o caminho pelo perfil. | 2 | 1 | 2 | 1.7 | backlog com prazo | aberto |
| 6 | Barra lateral | H8 · estética e minimalismo | Itens "em breve" ocupam espaço na navegação principal. | 1 | 1 | 1 | 1.0 | aceito até o RF chegar | aberto |
| 7 | Aparência | H1 · visibilidade do estado | Toast de confirmação cobre a última linha da página em telas baixas. | 1 | 1 | 0 | 0.7 | cosmético | aberto |
| 8 | Cadastro | H9 · ajudar a reconhecer e corrigir erros | Erros vêm todos juntos com mensagem por campo e foco no campo certo. | 0 | 0 | 0 | 0.0 | sem ação | fechado |
