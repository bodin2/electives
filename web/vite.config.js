import { execSync } from 'node:child_process'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import devtools from 'solid-devtools/vite'
import { defineConfig } from 'vite'
import solid from 'vite-plugin-solid'
import pkg from '../package.json'

const commit = execSync('git rev-parse --short HEAD').toString().trim()

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    const isProd = mode === 'production'

    return {
        plugins: [
            devtools({
                autoname: true,
                locator: true,
            }),
            tanstackRouter({ target: 'solid', autoCodeSplitting: isProd }),
            solid(),
        ],
        css: {
            transformer: 'lightningcss',
            lightningcss: {
                cssModules: {
                    pattern: isProd ? '[hash]-[local]' : '[name]-[hash]-[local]',
                },
                targets: {
                    chrome: 123 << 16,
                    firefox: 120 << 16,
                    safari: (17 << 16) | (5 << 8),
                },
            },
        },
        build: {
            cssMinify: 'lightningcss',
            cssTarget: ['chrome123', 'firefox120', 'safari17.5'],
        },
        define: {
            'process.env.APP_VERSION': JSON.stringify(pkg.version),
            'process.env.APP_COMMIT': JSON.stringify(commit),
            'process.env.API_BASE_URL': JSON.stringify(process.env.API_BASE_URL || 'http://127.0.0.1:8080'),
        },
        resolve: {
            // My setup includes linking m3-solid from source
            // which causes multiple copies of solid-js to be included
            dedupe: ['solid-js'],
        },
    }
})
