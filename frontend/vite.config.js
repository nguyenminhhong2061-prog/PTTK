import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // Proxy tất cả /api-exam/* → exam-service (port 5001)
      '/api-exam': {
        target: 'http://localhost:5001',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api-exam/, ''),
      },
      // Proxy tất cả /api-sub/* → submission-service (port 5002)
      '/api-sub': {
        target: 'http://localhost:5002',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api-sub/, ''),
      },
      // Proxy tất cả /api-stats/* → statistics-service (port 5003)
      '/api-stats': {
        target: 'http://localhost:5003',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api-stats/, ''),
      },
    },
  },
});
