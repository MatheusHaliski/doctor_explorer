/** Configuração só por variável de ambiente (nenhum segredo aqui: o SFU confere tokens com a chave pública da core-api). */
export const config = {
  port: Number(process.env.RTC_PORT ?? 4443),
  listenIp: process.env.RTC_LISTEN_IP ?? "127.0.0.1",
  announcedAddress: process.env.RTC_ANNOUNCED_ADDRESS ?? "127.0.0.1",
  minPort: Number(process.env.RTC_MIN_PORT ?? 40000),
  maxPort: Number(process.env.RTC_MAX_PORT ?? 40199),
  coreUrl: process.env.RTC_CORE_URL ?? "http://localhost:8080",
  allowedOrigins: (process.env.RTC_ALLOWED_ORIGINS ?? "http://localhost:3000").split(",").map((s) => s.trim()).filter(Boolean),
  maxPeers: Number(process.env.RTC_MAX_PEERS ?? 8),
  authTimeoutMs: 10_000,
};
