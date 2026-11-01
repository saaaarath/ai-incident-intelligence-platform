import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // Docker Compose publishes the log processor on host port 18084.
  // Override this with VITE_BACKEND_URL when running Spring Boot directly.
  const backendTarget = env.VITE_BACKEND_URL || 'http://localhost:18084';

  return {
    plugins: [
      react(),
      tailwindcss(),
    ],
    server: {
      port: 5173,
      proxy: {
        '/incidents': {
          target: backendTarget,
          changeOrigin: true,
          secure: false,
        },
        '/api': {
          target: backendTarget,
          changeOrigin: true,
          secure: false,
        },
        '/actuator': {
          target: backendTarget,
          changeOrigin: true,
          secure: false,
        }
      }
    }
  };
});
