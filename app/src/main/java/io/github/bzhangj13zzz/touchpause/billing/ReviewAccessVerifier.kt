package io.github.bzhangj13zzz.touchpause.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Verifies the reusable store-review code without embedding a forgeable secret in the app. */
object ReviewAccessVerifier {
    private const val CODE_PREFIX = "TPR1-"
    private const val SIGNED_MESSAGE = "touchpause-review-access-v1\n"
    private const val PUBLIC_KEY_BASE64 =
        "MCowBQYDK2VwAyEATrVOSkNK3i4wg8x4HbNoyhSSuC0QxOReWR8X188ZeVQ="

    fun isValid(code: String): Boolean = verify(code, PUBLIC_KEY_BASE64)

    /** Accepts an alternate public key so unit tests can exercise real Ed25519 signatures. */
    internal fun verify(code: String, publicKeyBase64: String): Boolean {
        if (!code.startsWith(CODE_PREFIX)) return false

        return try {
            val signatureBytes = Base64.getUrlDecoder().decode(code.removePrefix(CODE_PREFIX))
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                X509EncodedKeySpec(publicKeyBytes)
            )
            Signature.getInstance("Ed25519").run {
                initVerify(publicKey)
                update(SIGNED_MESSAGE.toByteArray(Charsets.UTF_8))
                verify(signatureBytes)
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }
}
