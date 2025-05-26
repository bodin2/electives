import { success } from './shared'

await Bun.build({
    entrypoints: ['src/app.ts'],
    target: 'bun',
    outdir: 'dist',
    format: 'esm',
    minify: true,
    sourcemap: 'external',
})

success('Build successful!')
