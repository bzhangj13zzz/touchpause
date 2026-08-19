package io.github.bzhangj13zzz.touchpause.billing

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAccessVerifierTest {
    @Test
    fun acceptsMatchingEd25519Signature() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update("touchpause-review-access-v1\n".toByteArray())
            sign()
        }
        val code = "TPR1-" + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        assertTrue(ReviewAccessVerifier.verify(code, publicKey))
        assertFalse(ReviewAccessVerifier.verify(code + "x", publicKey))
    }

    @Test
    fun rejectsMalformedCode() {
        assertFalse(ReviewAccessVerifier.isValid("not-a-review-code"))
    }
}
