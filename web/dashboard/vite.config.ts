import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// The dashboard calls the API on its own origin: CloudFront sends /auth/* and
// /sync/* to API Gateway (decision 111). The dev server does the same through a
// proxy, to staging unless DINOGYM_API_URL points elsewhere (the laptop's API).
const api = process.env.DINOGYM_API_URL ?? "https://ypn4asfnil.execute-api.us-east-1.amazonaws.com";

export default defineConfig(({ command }) => ({
  plugins: [react()],
  // The build lives under /app/ in the web bucket, next to the shared link page's
  // /web/ (infra/modules/dinogym/web.tf); the dev server stays at the root.
  base: command === "build" ? "/app/" : "/",
  server: {
    port: 5173,
    strictPort: true,
    // theme.css takes Pixelify Sans from the app's resources, outside this folder.
    fs: { allow: [".", "../../android/app/src/main/res/font"] },
    proxy: {
      "/auth": { target: api, changeOrigin: true },
      "/sync": { target: api, changeOrigin: true },
    },
  },
  test: {
    environment: "node",
  },
}));
