import { generateKeys } from 'paseto-ts/v4'

const { publicKey, secretKey } = generateKeys('public')

console.log('🔐 ELECTIVES_API_SECRET_KEY:', secretKey)
console.log('✅ ELECTIVES_API_PUBLIC_KEY:', publicKey)
