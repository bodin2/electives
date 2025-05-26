export function error(message: string): never {
    console.error(`❌ ${message}`)
    process.exit(1)
}

export function success(message: string, ...args: unknown[]): void {
    console.log(`✅ ${message}`, ...args)
}
