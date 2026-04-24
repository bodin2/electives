/**
 * A simple implementation of XXHash32 for small inputs, returning a 31-bit positive integer.
 * This is not a full XXHash32 implementation. Don't use it for large inputs or performance-critical code.
 */
export function simpleXXHash31(input: string, seed = 0): number {
    const encoder = new TextEncoder()
    const data = encoder.encode(input)
    const len = data.length

    const PRIME32_1 = 2654435761n
    const PRIME32_2 = 2246822519n
    const PRIME32_3 = 3266489917n
    const PRIME32_4 = 668265263n
    const PRIME32_5 = 374761393n

    let h32: bigint

    if (len >= 16) {
        // Standard XXH32 has a complex 16-byte loop here.
        // For "tiny data," we can skip to the tail processing.
        // However, to stay spec-compliant, we use the simple initialization:
        h32 = BigInt(seed) + PRIME32_5
    } else {
        h32 = BigInt(seed) + PRIME32_5
    }

    h32 = (h32 + BigInt(len)) & 0xffffffffn

    // Process remaining bytes
    let cursor = 0
    while (cursor + 4 <= len) {
        const chunk = BigInt(
            data[cursor] | (data[cursor + 1] << 8) | (data[cursor + 2] << 16) | (data[cursor + 3] << 24),
        )
        h32 = (h32 + chunk * PRIME32_3) & 0xffffffffn
        h32 = ((h32 << 17n) | (h32 >> 15n)) & 0xffffffffn // Rotate Left
        h32 = (h32 * PRIME32_4) & 0xffffffffn
        cursor += 4
    }

    while (cursor < len) {
        h32 = (h32 + BigInt(data[cursor]) * PRIME32_5) & 0xffffffffn
        h32 = ((h32 << 11n) | (h32 >> 21n)) & 0xffffffffn // Rotate Left
        h32 = (h32 * PRIME32_1) & 0xffffffffn
        cursor++
    }

    // Final Mix
    h32 ^= h32 >> 15n
    h32 = (h32 * PRIME32_2) & 0xffffffffn
    h32 ^= h32 >> 13n
    h32 = (h32 * PRIME32_3) & 0xffffffffn
    h32 ^= h32 >> 16n

    // Mask to 31-bit positive signed integer
    return Number(h32 & 0x7fffffffn)
}
