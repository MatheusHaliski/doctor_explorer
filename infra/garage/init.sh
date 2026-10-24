#!/bin/sh
# Inicializa o Garage local pela Admin API v2: layout de 1 nó, chave de acesso fixa de dev e bucket de mídia.
# Só para dev/it. Em produção o S3 é o da AWS (sa-east-1) e as credenciais vêm do ambiente.
set -eu
H="Authorization: Bearer $GARAGE_ADMIN_TOKEN"
until curl -sf -H "$H" "$GARAGE_ADMIN/v2/GetClusterStatus" >/dev/null; do echo "aguardando garage"; sleep 2; done

NODE=$(curl -sf -H "$H" "$GARAGE_ADMIN/v2/GetClusterStatus" | sed -n 's/.*"id":"\([0-9a-f]*\)".*/\1/p' | head -1)
if ! curl -sf -H "$H" "$GARAGE_ADMIN/v2/GetClusterLayout" | grep -q "$NODE"; then
  curl -sf -H "$H" -X POST "$GARAGE_ADMIN/v2/UpdateClusterLayout" \
    -d "{\"roles\":[{\"id\":\"$NODE\",\"zone\":\"dev\",\"capacity\":1073741824,\"tags\":[\"dev\"]}]}" >/dev/null
  V=$(curl -sf -H "$H" "$GARAGE_ADMIN/v2/GetClusterLayout" | sed -n 's/.*"version":\([0-9]*\).*/\1/p' | head -1)
  curl -sf -H "$H" -X POST "$GARAGE_ADMIN/v2/ApplyClusterLayout" -d "{\"version\":$((V + 1))}" >/dev/null
fi

curl -sf -H "$H" "$GARAGE_ADMIN/v2/ListKeys" | grep -q '"name":"de-dev"' || \
  curl -sf -H "$H" -X POST "$GARAGE_ADMIN/v2/ImportKey" \
    -d '{"name":"de-dev","accessKeyId":"GKdedevdedevdedevdedevdede","secretAccessKey":"de-dev-secret-de-dev-secret-de-dev-secret-de-dev-secret-0000000"}' >/dev/null

for B in de-media de-quarantine de-export; do
  curl -sf -H "$H" "$GARAGE_ADMIN/v2/ListBuckets" | grep -q "\"$B\"" || \
    curl -sf -H "$H" -X POST "$GARAGE_ADMIN/v2/CreateBucket" -d "{\"globalAlias\":\"$B\"}" >/dev/null
  BID=$(curl -sf -H "$H" "$GARAGE_ADMIN/v2/ListBuckets" | tr '{' '\n' | grep "\"$B\"" | sed -n 's/.*"id":"\([0-9a-f]*\)".*/\1/p' | head -1)
  curl -sf -H "$H" -X POST "$GARAGE_ADMIN/v2/AllowBucketKey" \
    -d "{\"bucketId\":\"$BID\",\"accessKeyId\":\"GKdedevdedevdedevdedevdede\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":true}}" >/dev/null
done
echo "garage pronto"
