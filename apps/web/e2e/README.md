# e2e · web real contra a API real

`flow.cjs` sobe no navegador o mesmo caminho de um médico: cadastro, código por e-mail (lido do log do
mailer dev), selo CRM/RQE (fonte simulada do CFM: prefixo `12` = regular), grupo, publicação bloqueada
por identificador, publicação válida, feed por quatro eixos, consentimentos, sudo, tema escuro, mobile,
logout e login com mensagem neutra. Sem mock de autenticação (a stack exige login real nos testes).

```bash
# 1. PostgreSQL + Valkey (docker compose up -d, ou instâncias locais)
# 2. API em dev, com o log em arquivo
cd apps/core-api && mvn -DskipTests package && SPRING_PROFILES_ACTIVE=dev java -jar target/core-api-*.jar > /tmp/api.log &
# 3. web
cd apps/web && pnpm build && pnpm start &            # ou pnpm dev
# 4. fluxo
API_LOG=/tmp/api.log SHOTS=e2e/shots node e2e/flow.cjs
```

`playwright` precisa estar resolvível (`pnpm add -D playwright` ou `PLAYWRIGHT_ROOT=$(npm root -g)`).
No CI o job `web-e2e` faz tudo isso com `services:` de PostGIS e Valkey.
