import { watch } from 'fs'
import { build } from './build'
import { resolve } from 'path'

const watcher = watch(
    resolve(import.meta.dir, '..', 'src'),
    {
        recursive: true,
    },
    async (_, filename) => {
        if (filename?.endsWith('.proto')) {
            console.info(`File changed: ${filename}`)
            await build(filename)
            console.info(`Rebuilt: ${filename}`)
        }
    },
)

console.info('Watching for changes in .proto files...')

process.on('SIGINT', () => {
    watcher.close()
    process.exit(0)
})
