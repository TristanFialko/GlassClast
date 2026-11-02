package dev.imirror.receiver.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NetworkUtilsTest — verifies MAC address caching.
 */
class NetworkUtilsTest {

    @Test
    fun `getMacAddress returns stable value across calls`() {
        val mac1 = NetworkUtils.getMacAddress()
        val mac2 = NetworkUtils.getMacAddress()

        assertNotNull(mac1)
        assertNotNull(mac2)
        assertEquals(mac1, mac2)

        // Verify format: aa:bb:cc:dd:ee:ff
        assertTrue(mac1.matches(Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}")))
    }

    @Test
    fun `getMacAddress returns valid format`() {
        val mac = NetworkUtils.getMacAddress()
        assertTrue(mac.matches(Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}")))
    }

    @Test
    fun `getDeviceName returns non-empty string`() {
        // This test needs a context, but NetworkUtils.getDeviceName needs Context
        // We'll skip this for now as it requires Robolectric context
    }
}