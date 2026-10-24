import type { NextConfig } from "next";

/** Origem da core-api. O navegador só fala com o próprio host do web: `/api/*` é reescrito para a API,
 *  então o cookie de sessão opaco e o XSRF-TOKEN ficam no mesmo site (sem CORS, sem token em localStorage). */
const API_ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_ORIGIN}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Referrer-Policy", value: "no-referrer" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(self)" },
        ],
      },
    ];
  },
};

export default nextConfig;
