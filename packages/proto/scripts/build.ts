import { platform } from 'os'
import { join } from 'path'

const protoPath = join(
    '..',
    '..',
    'node_modules',
    '.bin',
    platform() === 'win32' ? 'protoc-gen-ts_proto.exe' : 'protoc-gen-ts_proto',
)

Bun.spawnSync(
    [
        'bunx',
        'protoc',
        '--proto_path=./src',
        `--plugin=${protoPath}`,
        '--ts_proto_out=./src/generated',
        process.argv[2],
    ],
    {
        stdio: ['ignore', 'inherit', 'inherit'],
    },
)
