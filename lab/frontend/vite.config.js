import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Default dev server port matches the backend's CORS allow-list (5173).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
});
