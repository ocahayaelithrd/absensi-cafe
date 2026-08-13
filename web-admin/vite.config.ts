import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    // ExcelJS sendiri sudah di atas 500 kB; peringatan bawaan Vite tidak
    // menambah informasi apa pun untuk aplikasi admin internal.
    chunkSizeWarningLimit: 1500,
  },
});
