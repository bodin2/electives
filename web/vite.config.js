import { tanstackRouter } from '@tanstack/router-plugin/vite'
import devtools from 'solid-devtools/vite'
import { defineConfig } from 'vite'
import { ViteImageOptimizer as imageOptimizer } from 'vite-plugin-image-optimizer'
import solid from 'vite-plugin-solid'
import pkg from '../package.json'

const commit = await Bun.$`git rev-parse --short HEAD`.text().then(it => it.trim())

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        devtools({
            autoname: true,
            locator: true,
        }),
        tanstackRouter({ target: 'solid', autoCodeSplitting: true }),
        solid(),
        imageOptimizer({
            webp: {
                quality: 75,
            },
        }),
    ],
    define: {
        'process.env.APP_VERSION': JSON.stringify(pkg.version),
        'process.env.APP_COMMIT': JSON.stringify(commit),
    },
    resolve: {
        // My setup includes linking m3-solid from source
        // which causes multiple copies of solid-js to be included
        dedupe: ['solid-js'],
    },
})
