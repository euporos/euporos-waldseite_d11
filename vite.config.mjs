import { defineConfig } from 'vite';

export default defineConfig({
  publicDir: false,
  build: {
    outDir: 'public/compiled/bundle',
    emptyOutDir: true,
    sourcemap: true,
    rollupOptions: {
      input: {
        main:   'js-src/index.js',
        preise: 'js-src/preise.js',
      },
      output: {
        entryFileNames: '[name].js',
        assetFileNames: '[name].[ext]',
      },
    },
  },
});
