package th.ac.bodin2.electives.api.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.paseto4j.commons.PrivateKey
import org.paseto4j.commons.PublicKey
import org.paseto4j.version4.Paseto
import org.paseto4j.commons.Version
import java.security.KeyFactory
import java.security.SignatureException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.util.Base64

object Paseto {
    private val privateKey = PrivateKey(loadPrivateKey(), Version.V4)
    private val publicKey = PublicKey(loadPublicKey(), Version.V4)

    val ISSUER = getEnv("PASETO_ISSUER") ?: "electives.bodin2.ac.th"


    fun sign(payload: PasetoClaims): String = Paseto.sign(privateKey, Json.encodeToString(payload))

    /**
     * Verifies the given PASETO token and returns the claims if valid.
     *
     * @throws IllegalArgumentException if the token does not represent [PasetoClaims], is invalid, expired, or has incorrect issuer/audience.
     * @throws SignatureException if the token signature is invalid.
     * @throws kotlinx.serialization.SerializationException if the token payload cannot be deserialized.
     */
    fun verify(token: String): PasetoClaims {
        val json = Paseto.parse(publicKey, token)
        val claims = Json.decodeFromString(PasetoClaims.serializer(), json)

        if (claims.iss != ISSUER) {
            throw IllegalArgumentException("Invalid issuer: ${claims.iss}")
        }

        val nbf = LocalDateTime.parse(if (!claims.nbf.isNullOrBlank()) claims.nbf else claims.iat, ISO_OFFSET_DATE_TIME)
        val exp = LocalDateTime.parse(claims.exp, ISO_OFFSET_DATE_TIME)
        val now = LocalDateTime.now()

        if (now.isBefore(nbf) || now.isAfter(exp)) {
            throw IllegalArgumentException("Token is not valid at this time: nbf=$nbf, exp=$exp")
        }

        return claims
    }


    private fun loadPrivateKey(): java.security.PrivateKey {
        val base64 = getEnv("PASETO_PRIVATE_KEY")
            ?: error("Environment variable PASETO_PRIVATE_KEY is not set")

        val keyBytes = Base64.getDecoder().decode(base64)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("Ed25519")
        return keyFactory.generatePrivate(spec)
    }

    private fun loadPublicKey(): java.security.PublicKey {
        val base64 = getEnv("PASETO_PUBLIC_KEY")
            ?: error("Environment variable PASETO_PUBLIC_KEY is not set")

        val keyBytes = Base64.getDecoder().decode(base64)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("Ed25519")
        return keyFactory.generatePublic(spec)
    }
}

@Serializable
data class PasetoClaims(
    val iss: String,
    val sub: String,
    val aud: String,
    val exp: String,
    val nbf: String? = null,
    val iat: String,
    val jti: String? = null,
) {
    companion object {
        fun from(
            iss: String,
            sub: String,
            aud: String,
            exp: OffsetDateTime,
            nbf: OffsetDateTime? = null,
            jti: String? = null,
        ) = PasetoClaims(
            iss = iss.ifEmpty { throw IllegalArgumentException("Issuer cannot be empty") },
            sub = sub.ifEmpty { throw IllegalArgumentException("Subject cannot be empty") },
            aud = aud.ifEmpty { throw IllegalArgumentException("Audience cannot be empty") },
            exp = exp.format(ISO_OFFSET_DATE_TIME),
            nbf = nbf?.format(ISO_OFFSET_DATE_TIME),
            iat = OffsetDateTime.now().format(ISO_OFFSET_DATE_TIME),
            jti = jti,
        )
    }
}