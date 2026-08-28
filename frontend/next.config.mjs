const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    return [
      // Proxyer /api/* og /auth/* til backend slik at nettleseren kun snakker med
      // Next.js sitt origin (ingen CORS nødvendig). Viktig: /auth/* må også proxys,
      // ikke bare /api/*, ellers blir sesjonscookien satt på backend sitt origin
      // (fra OAuth-callback) og forsvinner igjen på neste /api/*-kall.
      { source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` },
      { source: "/auth/:path*", destination: `${BACKEND_URL}/auth/:path*` },
      { source: "/join/:path*", destination: `${BACKEND_URL}/join/:path*` },
    ];
  },
};

export default nextConfig;
