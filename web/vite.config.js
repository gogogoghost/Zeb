import { defineConfig } from 'vite'
export default defineConfig({
    base:'/',
    server: {
        open: false,
        port: 3000,
        host: '0.0.0.0'
    },
})
