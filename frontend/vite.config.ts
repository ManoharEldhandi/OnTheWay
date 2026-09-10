import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// During development the frontend runs on :5173 and proxies API calls to the
// backend on :8080, so there are no CORS surprises and no hardcoded host.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080';
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
        '/ws': {
          target: apiTarget.replace(/^http/, 'ws'),
          ws: true,
          changeOrigin: true,
        },
      },
    },
  };
});
