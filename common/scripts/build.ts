import { mkdirSync } from 'fs'
import { platform } from 'os'
import { join } from 'path'

if (import.meta.main) await build(process.argv[2])

export function build(filename: string): Promise<void> {
    let rs: () => void
    let rj: (reason?: unknown) => void

    const promise = new Promise<void>((resolve, reject) => {
        rs = resolve
        rj = reject
    })

    mkdirSync(join(process.cwd(), 'build', 'ts'), { recursive: true })

    const protoPath = join(
        'node_modules',
        '.bin',
        platform() === 'win32' ? 'protoc-gen-ts_proto.exe' : 'protoc-gen-ts_proto',
    )

    Bun.spawn(
        ['bunx', 'protoc', '--proto_path=./src', `--plugin=${protoPath}`, '--ts_proto_out=./build/ts', filename],
        {
            stdio: ['ignore', 'inherit', 'inherit'],
            onExit: (_, code) => {
                if (code) rj(`Process exited with code ${code}`)
                else rs()
            },
        },
    )

    return promise
}
