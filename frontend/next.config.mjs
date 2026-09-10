const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    return [

      { source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` },
      { source: "/auth/:path*", destination: `${BACKEND_URL}/auth/:path*` },
      { source: "/join/:path*", destination: `${BACKEND_URL}/join/:path*` },
    ];
  },
};

export default nextConfig;
