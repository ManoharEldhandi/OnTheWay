import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// During development the frontend runs on :5173 and proxies API calls to the
// backend on :8080, so there are no CORS surprises and no hardcoded host.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
