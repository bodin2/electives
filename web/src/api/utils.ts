const encoder = new TextEncoder()

export function encodeToBytes(input: string): Uint8Array {
    return encoder.encode(input)
}

export function sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
}
