import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const inDocker = process.env.DOCKER_ENV === 'true';
const examTarget =
  process.env.VITE_PROXY_EXAM_TARGET ||
  (inDocker ? 'http://exam-service:8080' : 'http://localhost:5001');
const submissionTarget =
  process.env.VITE_PROXY_SUB_TARGET ||
  (inDocker ? 'http://submission-service:8080' : 'http://localhost:5002');
const statisticsTarget =
  process.env.VITE_PROXY_STATS_TARGET ||
  (inDocker ? 'http://statistics-service:8080' : 'http://localhost:5003');

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // Proxy tất cả /api-exam/* → exam-service (port 5001)
      '/api-exam': {
        target: examTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api-exam/, ''),
      },
      // Proxy tất cả /api-sub/* → submission-service (port 5002)
      '/api-sub': {
        target: submissionTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api-sub/, ''),
      },
      // Proxy tất cả /api-stats/* → statistics-service (port 5003)
      '/api-stats': {
        target: statisticsTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api-stats/, ''),
      },
    },
  },
});
