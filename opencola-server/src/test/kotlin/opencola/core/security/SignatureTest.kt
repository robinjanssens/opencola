package opencola.core.security

import io.opencola.core.security.generateKeyPair
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.sign
import kotlin.test.Test

class SignatureTest {
    @Test
    fun testValidSignature() {
        val data = "hello".toByteArray()
        val keyPair = generateKeyPair()
        val signature = sign(keyPair.private, data)
        assert(isValidSignature(keyPair.public, data, signature))
    }

    @Test
    fun testInvalidSignature() {
        val data1 = "hello".toByteArray()
        val data2 = "hi".toByteArray()
        val keyPair = generateKeyPair()
        val signature = sign(keyPair.private, data1)
        assert(!isValidSignature(keyPair.public, data2, signature))
    }
}