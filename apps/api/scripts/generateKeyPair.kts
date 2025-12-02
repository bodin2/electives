#!/usr/bin/env kotlin

import java.security.KeyPairGenerator
import java.security.KeyPair
import java.util.Base64

val keyGen = KeyPairGenerator.getInstance("Ed25519")
val keyPair: KeyPair = keyGen.generateKeyPair()

val privateKey = keyPair.private
val publicKey  = keyPair.public

val privateB64 = Base64.getEncoder().encodeToString(privateKey.encoded)
val publicB64  = Base64.getEncoder().encodeToString(publicKey.encoded)

println("PASETO_PRIVATE_KEY=\"$privateB64\"")
println("PASETO_PUBLIC_KEY=\"$publicB64\"")
