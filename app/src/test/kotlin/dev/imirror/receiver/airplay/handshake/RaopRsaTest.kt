package dev.imirror.receiver.airplay.handshake

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.Cipher

/**
 * Verifies the legacy `rsaaeskey` recovery: a 16-byte key encrypted with the embedded AirPort
 * Express *public* key (RSA-2048, OAEP/SHA-1) must decrypt back to the same bytes via [RaopRsa].
 * This proves the embedded PKCS#8 key loads and the OAEP plumbing matches what real senders use,
 * without needing an actual legacy AirPlay sender. Pure JCA — no native lib, no Android Base64.
 */
class RaopRsaTest {

    private fun encryptWithEmbeddedPublicKey(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, RaopRsa.publicKeyForTest())
        return cipher.doFinal(plain)
    }

    @Test
    fun `round-trips a 16-byte AES key through the embedded AirPort Express key`() {
        // Inject the test fixture key (same bytes as production, but test sources aren't shipped in APK)
        RaopRsa.setTestKeyProvider { Companion.hexToBytes(Companion.TEST_KEY_PKCS8_HEX) }

        val key = ByteArray(16) { (it * 7 + 1).toByte() }
        val blob = encryptWithEmbeddedPublicKey(key)
        assertEquals("RSA-2048 ciphertext is 256 bytes", 256, blob.size)
        assertArrayEquals(key, RaopRsa.decryptAesKey(blob))
    }

    @Test
    fun `malformed blob returns null rather than throwing`() {
        RaopRsa.setTestKeyProvider { Companion.hexToBytes(Companion.TEST_KEY_PKCS8_HEX) }
        assertNull(RaopRsa.decryptAesKey(ByteArray(256) { 0x5A }))
    }

    companion object {
        // Test fixture: same PKCS#8 DER hex as production (test sources are NOT shipped in APK).
        private const val TEST_KEY_PKCS8_HEX =
            "308204bf020100300d06092a864886f70d0101010500048204a9308204a50201000282010100e7d744f2a2e2788b6c1f55a08eb70544a8fa7945aa8be6c62ce5f51cbdd4dc6842fe3d1083dd2edec1bfd4252dc02e6f398bdf0e6148ea84855e2e442da6d62664f674a1f304929ade4f6893ef2df6e711a8c77a0d91c9d980822e50d12922afea40ea9f0e14c0f76938c5f3882fc0323dd9fe55155f51bb5921c201629fd73352d5e2efaabf9ba048d7b813a2b6767f6c3ccf1eb4ce673d037b0d2ea30c5fffeb06f8d08adde409571a9c689fef10728855dd8cfb9a8bef5c8943ef3b5faa15dde698beddf3599603eb3e6f61372bb628f6559f599a78bf500687aa7f4976c0562d412956f8989e18a6355bd81597825e0fc875343ec782117625cdbf98447b02030100010282010100e5f00c72f577d604b9a4ce4122aa84b01743ec995acfcc7f4ab27c0b187f90665be359df1259818deeed79d3b1ef845e4ddddac9a155373b5e270d8e1315001a2e527d54cdf9000a5768bc98d4446b37bbbd00b29dd8b53062133b2a6e77f4ee32505622904da720fb1c12c03996da713a0506098edbedecf936d0fa9cbd5929abb0eda35799502f9894dcb8fc569a892d17780324a2b6c3166e346709134b854041b867706b58fef2a0db922b77628b68e69693c7af43bf2a73d0b732377a0ba17b44f051e9bf79849dcb3332571fd8a70933c2d60bdec479934a3daca40bb6f2f37c0a9d07106eadc8b369a03f2f41c880098e8add46240dac68cc5354f36102818100f7e0bf5a1e6718319a8b6209c31714440459f973856613b17ae1508bb3e6316e6b7f462d2f7d64412b84b76bc23f2b0c3562455279b243a9f7316f958007b34c61f768e2d44ed5ff2b272817ec32b3e493929228fae78e774ca0f75ebd69d59202798f116e360c6438b32e1bd8b9dc1e3232f0d30918883cc43ef8dda22c369102818100ef6ffff994f1e56441aa0035fd19a0c8d6f02378c70580d9c48420791df407c591fb6ebfca322c3086dd901fd2fae1aebb64adf6bb79ff8051bebd0cd820ab8987400601a7b2fe9390cacc9acab8ed2bf91d186d8f69643d7efe0f5d56df7577a2d035ea5413fc98d8f3f908da059a379da4b1cc38f15d560a83cc317153c84b02818100d0ebafbc4025ba818c75702334384e8f696f804d7aa0e7764e507bb7d3dfefc7d678c6682d3fad713441beeae724a09ec09bdc3bc0709c9133d489ece2a51add053127490f9286d173c8a4054dc20a575c7e4c0c9834f4a1de874917a3e400eaf885062db5cb7e343689e711f75fe783d7e19192fd769cd542bea4b90107ecd10281807f4018dc7dea292da53042386f3105a0778adc6f3de690da2b74c5055983edf574661a2fd7b7de8053ccc0e208f0c8ac626f597d3d99d2ce51a37b39ae4b7e9ef2c075f0bf3d83cacd32da969192c2899235825c07d1cd3259a1906cdcd499cb613e22c94cb1ea971906609df1b0f48b063f17372034369499b5fdf970ef440d02818100904ee920f944ef5aaf7c9420a00f5e9b48082c0b84e0fbb5dda2a22677dfb7b8488db2bee64c9bdd3cac66fa320e76f71ce2af2272bbbd76cab94e084a0c41d9b0771dc63340c1accf5a89da01b437986f269cf0c216e15ea14a038cda692af0eb6db00e78802b9325204d2d20028a3f8cb13468e80f64188e1046ba1be458a6"

        private fun hexToBytes(s: String): ByteArray =
            ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte() }
    }
}